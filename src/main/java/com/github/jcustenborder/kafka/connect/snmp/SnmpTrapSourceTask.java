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
import org.snmp4j.security.AuthMD5;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.Priv3DES;
import org.snmp4j.security.PrivAES128;
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

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SnmpTrapSourceTask extends SourceTask implements CommandResponder {
  static final Logger log = LoggerFactory.getLogger(SnmpTrapSourceTask.class);

  @Override
  public String version() {
    return VersionUtil.getVersion();
  }

  SnmpTrapSourceConnectorConfig config;
  AbstractTransportMapping<?> transport;
  ThreadPool threadPool;
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

    log.info("start() - Setting listen address with {} on {}:{}", this.config.listenProtocol, this.config.listenAddress, this.config.listenPort);
    log.info("start() - MPv3 support: {}", this.config.mpv3Enabled);

    this.transport = setupTransport(this.config.listenAddress, this.config.listenProtocol, this.config.listenPort);

    log.info("start() - Configuring ThreadPool DispatchPool to {} thread(s)", this.config.dispatcherThreadPoolSize);
    this.threadPool = ThreadPool.create("DispatchPool", this.config.dispatcherThreadPoolSize);
    this.messageDispatcher = createMessageDispatcher(this.threadPool, this.config.mpv3Enabled);
    SecurityProtocols securityProtocols = setupSecurityProtocols(this.config.mpv3Enabled);

    try {
      this.transport.listen();
    } catch (IOException e) {
      throw new ConnectException("Exception thrown while calling transport.listen()", e);
    }

    this.snmp = new Snmp(this.messageDispatcher, this.transport);
    this.snmp.addCommandResponder(this);

    if (this.config.mpv3Enabled) {
      log.debug("Setting up Mpv3 with protocols {} and {}", this.config.authenticationProtocol, this.config.privacyProtocol);
      setupMpv3Usm(this.snmp, this.config, securityProtocols);
    }

  }


  private static List<SourceRecord> drain(int batchSize, RecordBuffer<SourceRecord> records) {
    int size = Math.min(records.size(), batchSize);
    log.debug("Non-empty buffer, draining {} records", size);
    List<SourceRecord> batch = new ArrayList<>(size);

    for (int i = 0; i < size; i++) {
      SourceRecord record = records.poll();
      if (null != record) {
        batch.add(record);
      } else {
        break;
      }
    }

    return batch.isEmpty() ? null : batch; // We want this to be null according to Kafka Connect poll() spec
  }

  @Override
  public List<SourceRecord> poll() {
    try {
      if (this.recordBuffer.isEmpty()) {
        Thread.sleep(this.config.pollBackoffMs);
      } else {
        return drain(this.config.batchSize, this.recordBuffer);
      }
    } catch (Exception err) {
      log.error("poll() - Issue with draining", err);
    }
    return null;
  }

  @Override
  public void stop() {
    log.info("stop() - stopping threadpool");

    if (this.threadPool != null) {
      this.threadPool.cancel();
    }

    log.info("stop() - closing transport.");
    try {
      if (this.transport != null) {
        this.transport.close();
      } else {
        log.error("Transport was null.");
      }
    } catch (IOException e) {
      log.error("Exception thrown while closing transport.", e);
    }
    this.recordBuffer.clear();

  }

  @Override
  public void processPdu(CommandResponderEvent event) {
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
      securityProtocols.addPrivacyProtocol(new Priv3DES());
      securityProtocols.addPrivacyProtocol(new PrivAES128());
    }

    return securityProtocols;
  }

  private OID convertPrivacyProtocol(PrivacyProtocol privacyProtocol) {
    switch (privacyProtocol) {
      case DES3:
        return Priv3DES.ID;
      case AES128:
        return PrivAES128.ID;
      default:
        return PrivAES128.ID;
    }
  }

  private OID convertAuthenticationProtocol(AuthenticationProtocol authenticationProtocol) {
    switch (authenticationProtocol) {
      case MD5:
        return AuthMD5.ID;
      case SHA:
        return AuthSHA.ID;
      default:
        return AuthMD5.ID;
    }
  }

  private void setupMpv3Usm(Snmp snmp, SnmpTrapSourceConnectorConfig config, SecurityProtocols sp) {
    MPv3 mpv3 = ((MPv3) snmp.getMessageProcessingModel(MPv3.ID));
    USM usm = new USM(sp, new OctetString("SNMP Connector"), 0);
    usm.setEngineDiscoveryEnabled(true);
    SecurityModels sm = SecurityModels.getInstance().addSecurityModel(usm);
    if (Utils.noneNull(config.username, config.privacyPassphrase, config.authenticationPassphrase)) {
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

  private static MessageDispatcher createMessageDispatcher(ThreadPool threadPool, boolean mpv3Enabled) {
    MultiThreadedMessageDispatcher md = new MultiThreadedMessageDispatcher(threadPool, new MessageDispatcherImpl());
    md.addMessageProcessingModel(new MPv1());
    md.addMessageProcessingModel(new MPv2c());
    if (mpv3Enabled) {
      md.addMessageProcessingModel(new MPv3());
    }
    return md;
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
}