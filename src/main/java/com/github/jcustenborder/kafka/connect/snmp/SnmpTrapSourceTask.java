/**
 * Copyright © 2021 Elisa Oyj
 * Copyright © 2017 Jeremy Custenborder (jcustenborder@gmail.com)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jcustenborder.kafka.connect.snmp;

import com.github.jcustenborder.kafka.connect.snmp.enums.AuthenticationProtocol;
import com.github.jcustenborder.kafka.connect.snmp.enums.PrivacyProtocol;
import com.github.jcustenborder.kafka.connect.snmp.monitor.SnmpMetrics;
import com.github.jcustenborder.kafka.connect.snmp.pdu.PDUConverter;
import com.github.jcustenborder.kafka.connect.snmp.utils.RecordBuffer;
import com.github.jcustenborder.kafka.connect.snmp.utils.Utils;
import org.apache.kafka.common.utils.SystemTime;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.MessageDispatcher;
import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.AuthHMAC384SHA512;
import org.snmp4j.security.AuthMD5;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.Priv3DES;
import org.snmp4j.security.PrivAES128;
import org.snmp4j.security.PrivAES256;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TcpAddress;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.transport.AbstractTransportMapping;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.MultiThreadedMessageDispatcher;
import org.snmp4j.util.ThreadPool;
import org.weakref.jmx.MBeanExporter;

import javax.management.MBeanServer;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

public class SnmpTrapSourceTask extends SourceTask implements CommandResponder {
  static final Logger log = LoggerFactory.getLogger(SnmpTrapSourceTask.class);
  private MBeanServer mbs;
  private MBeanExporter exporter;
  private SnmpMetrics metrics;
  private final String metricsName = "com.github.jcustenborder.kafka.connect.snmp:name=Metrics";

  @Override
  public String version() {
    return VersionUtil.getVersion();
  }

  SnmpTrapSourceConnectorConfig config;
  AbstractTransportMapping<?> transport;
  MessageDispatcher messageDispatcher;
  private Snmp snmp;
  PDUConverter converter;
  Time time = new SystemTime();
  private RecordBuffer<SourceRecord> recordBuffer;

  @Override
  public void start(Map<String, String> settings) {
    this.config = new SnmpTrapSourceConnectorConfig(settings);
    this.converter = new PDUConverter(this.time, config);
    this.recordBuffer = new RecordBuffer<>();
    this.metrics = new SnmpMetrics();

    if (config.collectSnmpMetrics) {
      try {
        wireMetricsToJMX(this.metrics);
      } catch (Exception err) {
        log.warn("start() - could not wire metrics to JMX");
      }
    }

    log.info("start() - Setting listen address with {} on {}:{}", this.config.listenProtocol, this.config.listenAddress, this.config.listenPort);
    log.info("start() - MPv3 support: {}", this.config.mpv3Enabled);

    this.transport = setupTransport(this.config.listenAddress, this.config.listenProtocol, this.config.listenPort);

    if (this.config.snmp4jUseMultithreaded) {
      log.info("start() - Configuring ThreadPool DispatchPool to {} thread(s)", this.config.dispatcherThreadPoolSize);
      ThreadPool pool = ThreadPool.create("DispatchPool", this.config.dispatcherThreadPoolSize);
      log.info("start() - Configuring multithreaded message dispatcher");
      this.messageDispatcher = createMultiMessageDispatcher(pool, this.config.mpv3Enabled);
    } else {
      log.info("start() - Configuring single threaded dispatcher");
      this.messageDispatcher = createSingleMessageDispatcher(this.config.mpv3Enabled);
      this.transport.setAsyncMsgProcessingSupported(false);
    }

    SecurityProtocols securityProtocols = setupSecurityProtocols(this.config.mpv3Enabled);
    this.snmp = new Snmp(this.messageDispatcher, this.transport);
    this.snmp.addCommandResponder(this);

    if (this.config.mpv3Enabled) {
      log.debug("Setting up Mpv3 with protocols {} and {}", this.config.authenticationProtocol, this.config.privacyProtocol);
      setupMpv3Usm(this.snmp, this.config, securityProtocols);
    }

    try {
      this.transport.listen();
      this.transport.setPriority(java.lang.Thread.MAX_PRIORITY); // Set the listener as highest priority
    } catch (IOException e) {
      throw new ConnectException("Exception thrown while calling transport.listen()", e);
    }

  }

  private void wireMetricsToJMX(SnmpMetrics metrics) {
    mbs = ManagementFactory.getPlatformMBeanServer();
    exporter = new MBeanExporter(mbs);
    exporter.export(metricsName, metrics);
  }

  @Override
  public List<SourceRecord> poll() {
    try {
      if (this.recordBuffer.isEmpty()) {
        Thread.sleep(this.config.pollBackoffMs);
      } else {
        log.debug("poll() - Non-empty buffer, draining {} records", Math.min(recordBuffer.size(), config.batchSize));
        if (this.config.snmp4jUseMultithreaded) {
          log.debug("poll() - Pending snmp requests count {}", this.snmp.getPendingAsyncRequestCount());
        } else {
          log.debug("poll() - Pending snmp requests count {}", this.snmp.getPendingSyncRequestCount());
        }
        List<SourceRecord> batch = recordBuffer.drain(this.config.batchSize);
        metrics.addPolled(batch.size());
        return batch.isEmpty() ? null : batch; // We want this to be null according to Kafka Connect poll() spec
      }
    } catch (Exception err) {
      log.error("poll() - Issue with draining", err);
    }
    return null;
  }

  @Override
  public void stop() {
    log.info("stop() - closing transport.");
    try {
      if (this.transport != null) {
        this.transport.close();
      } else {
        log.error("Transport was null.");
      }
      log.info("stop() - closing dispatcher");
      this.messageDispatcher.stop();
    } catch (IOException e) {
      log.error("Exception thrown while closing transport.", e);
    }

    try {
      if (mbs != null && exporter != null) {
        exporter.unexport(metricsName);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void processPdu(CommandResponderEvent event) {
    metrics.incrementToProcess();
    log.debug("processPdu() - Received event from {}", event.getPeerAddress());
    PDU pdu = event.getPDU();

    log.debug("processPdu() - Received PDU is {}", pdu);

    if (null == pdu) {
      log.warn("Null PDU received from {}", event.getPeerAddress());
      return;
    }

    if (PDU.TRAP != pdu.getType()) {
      log.debug("Message received from {} was not a trap. message={}", event.getPeerAddress(), event);
      return;
    }

    SourceRecord sourceRecord = converter.convert(event);
    this.recordBuffer.add(sourceRecord);
    metrics.incrementProcessed();
  }

  private static AbstractTransportMapping<?> setupTransport(String address, String listenProtocol, int port) {
    InetAddress inetAddress = setupAddress(address);

    try {
      if ("UDP".equals(listenProtocol)) {
        return setupUdpTransport(inetAddress, port);
      } else {
        return setupTcpTransport(inetAddress, port);
      }
    } catch (IOException ex) {
      throw new ConnectException("Exception thrown while configuring transport.", ex);
    }
  }

  private static DefaultUdpTransportMapping setupUdpTransport(InetAddress addr, int port) throws IOException {
    UdpAddress udpAddress = new UdpAddress(addr, port);
    return new DefaultUdpTransportMapping(udpAddress);
  }

  private static DefaultTcpTransportMapping setupTcpTransport(InetAddress addr, int port) throws IOException {
    TcpAddress tcpAddress = new TcpAddress(addr, port);
    return new DefaultTcpTransportMapping(tcpAddress);
  }

  private static InetAddress setupAddress(String listenAddress) throws ConnectException {
    try {
      return InetAddress.getByName(listenAddress);
    } catch (UnknownHostException e) {
      throw new ConnectException("Exception thrown while trying to resolve " + listenAddress, e);
    }
  }

  private static SecurityProtocols setupSecurityProtocols(boolean mpv3Enabled) {
    SecurityProtocols securityProtocols = SecurityProtocols.getInstance();
    securityProtocols.addDefaultProtocols();

    if (mpv3Enabled) {
      securityProtocols.addAuthenticationProtocol(new AuthMD5());
      securityProtocols.addAuthenticationProtocol(new AuthSHA());
      securityProtocols.addAuthenticationProtocol(new AuthHMAC384SHA512());
      securityProtocols.addPrivacyProtocol(new Priv3DES());
      securityProtocols.addPrivacyProtocol(new PrivAES128());
      securityProtocols.addPrivacyProtocol(new PrivAES256());
    }

    return securityProtocols;
  }

  private OID convertPrivacyProtocol(PrivacyProtocol privacyProtocol) {
    return switch (privacyProtocol) {
      case DES3 -> Priv3DES.ID;
      case AES128 -> PrivAES128.ID;
      case AES256 -> PrivAES256.ID;
    };
  }

  private OID convertAuthenticationProtocol(AuthenticationProtocol authenticationProtocol) {
    return switch (authenticationProtocol) {
      case MD5 -> AuthMD5.ID;
      case SHA -> AuthSHA.ID;
      case SHA2_512 -> AuthHMAC384SHA512.ID;
    };
  }

  private void setupMpv3Usm(Snmp snmp, SnmpTrapSourceConnectorConfig config, SecurityProtocols sp) {
    MPv3 mpv3 = ((MPv3) snmp.getMessageProcessingModel(MPv3.ID));
    USM usm = new USM(sp, new OctetString("SNMP Connector"), 0);
    usm.setEngineDiscoveryEnabled(true);
    SecurityModels sm = SecurityModels.getInstance().addSecurityModel(usm);
    if (config.noAuthNoPrivEnabled) {
      UsmUser uu = new UsmUser(
              new OctetString(config.username),
              null,
              null,
              null,
              null
      );
      usm.addUser(uu);
      log.info("Added user {} to handle MPv3 NoAuthNoPriv", config.username);
    } else if (Utils.noneNull(config.username, config.privacyPassphrase, config.authenticationPassphrase)) {
      UsmUser uu = new UsmUser(
          new OctetString(config.username),
          convertAuthenticationProtocol(config.authenticationProtocol),
          new OctetString(config.authenticationPassphrase),
          convertPrivacyProtocol(config.privacyProtocol),
          new OctetString(config.privacyPassphrase)
      );
      usm.addUser(uu);
      log.info("Added user {} to handle MPv3", config.username);
    }
    mpv3.setSecurityModels(sm);
  }

  private static MessageDispatcher addMessageProcessingModels(MessageDispatcher md, boolean mpv3Enabled) {
    md.addMessageProcessingModel(new MPv1());
    md.addMessageProcessingModel(new MPv2c());
    if (mpv3Enabled) {
      md.addMessageProcessingModel(new MPv3());
    }
    return md;
  }

  private static MessageDispatcher createSingleMessageDispatcher(boolean mpv3Enabled) {
    MessageDispatcher md = new MessageDispatcherImpl();
    return addMessageProcessingModels(md, mpv3Enabled);
  }

  private static MessageDispatcher createMultiMessageDispatcher(ThreadPool threadPool, boolean mpv3Enabled) {
    MultiThreadedMessageDispatcher md = new MultiThreadedMessageDispatcher(threadPool, new MessageDispatcherImpl());
    return addMessageProcessingModels(md, mpv3Enabled);
  }

  public RecordBuffer<SourceRecord> getRecordBuffer() {
    return recordBuffer;
  }

  public SnmpTrapSourceConnectorConfig getConfig() {
    return config;
  }

  public Snmp getSnmp() {
    return snmp;
  }

  public SnmpMetrics getMetrics() {
    return metrics;
  }
}