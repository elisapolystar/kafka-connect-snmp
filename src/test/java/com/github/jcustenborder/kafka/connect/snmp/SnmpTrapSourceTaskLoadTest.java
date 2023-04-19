/**
 * Copyright © 2021 Elisa Oyj
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

import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.SNMP4JSettings;
import org.snmp4j.Snmp;
import org.snmp4j.UserTarget;
import org.snmp4j.mp.DefaultCounterListener;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.AuthHMAC384SHA512;
import org.snmp4j.security.AuthMD5;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.PrivAES256;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.github.jcustenborder.kafka.connect.snmp.pdu.PDUGen.createV2Trap;
import static com.github.jcustenborder.kafka.connect.snmp.pdu.PDUGen.createV3Trap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


@Tag("load")
public class SnmpTrapSourceTaskLoadTest {
  private SnmpTrapSourceTask task;
  private CommunityTarget<Address> v2Target;

  private List<UserTarget<Address>> validV3Targets;
  private Snmp sendingSnmp;

  private final String defaultPrivacyPassphrase = "_0987654321_";
  private final String defaultAuthPassphrase = "_12345678_";


  List<V3User> validUsers = List.of(
      new V3User("user1", defaultPrivacyPassphrase, defaultAuthPassphrase, AuthMD5.ID),
      new V3User("user2", defaultPrivacyPassphrase, defaultAuthPassphrase, AuthSHA.ID),
      new V3User("user3", defaultPrivacyPassphrase, defaultAuthPassphrase, AuthHMAC384SHA512.ID),
      new V3User("user4", defaultPrivacyPassphrase, defaultAuthPassphrase, AuthHMAC384SHA512.ID, PrivAES256.ID)
  );
  List<V3User> invalidUsers = List.of(
      new V3User("fake", defaultPrivacyPassphrase, defaultAuthPassphrase)
  );

  static {
    SNMP4JSettings.setForwardRuntimeExceptions(true);
    SNMP4JSettings.setSnmp4jStatistics(SNMP4JSettings.Snmp4jStatistics.extended);
    try {
      setupBeforeClass();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @BeforeAll
  public static void setupBeforeClass() throws Exception {
    SNMP4JSettings.setExtensibilityEnabled(true);
    SecurityProtocols.getInstance().addDefaultProtocols();
  }

  @AfterAll
  public static void tearDownAfterClass() throws Exception {
    SecurityProtocols.setSecurityProtocols(null);
    SNMP4JSettings.setExtensibilityEnabled(false);
  }

  private List<UsmUser> convertUsers(List<V3User> users) {
    return users.stream().map(V3User::toUsm).collect(Collectors.toList());
  }

  private void addUsmsToSending(List<UsmUser> users) {
    users.forEach(usmUser -> {
      sendingSnmp.getUSM().addUser(usmUser);
    });
  }

  private void addUsmsToReceiving(List<UsmUser> users) {
    users.forEach((usmUser) -> {
      this.task.getSnmp().getUSM().addUser(usmUser);
    });
  }

  private void addUsms() {
    addUsmsToSending(convertUsers(validUsers));
    addUsmsToReceiving(convertUsers(validUsers));

    // Let's add a invalid user only for sending to verify functionality
    addUsmsToSending(convertUsers(invalidUsers));
  }

  private static CommunityTarget<Address> createCommunityTarget(Address address, String community) {
    CommunityTarget<Address> communityTarget = new CommunityTarget<>();
    communityTarget.setCommunity(new OctetString(community));
    communityTarget.setVersion(SnmpConstants.version2c);
    communityTarget.setAddress(address);
    return communityTarget;
  }

  private static UserTarget<Address> createUserTarget(Address address, String username, byte[] authorativeEngineId) {
    UserTarget<Address> ut = new UserTarget<>(address, new OctetString(username), authorativeEngineId);
    ut.setVersion(SnmpConstants.version3);
    return ut;
  }

  private static void noPendingSnmpRequests(Snmp snmp) {
    // Consume all
    while (snmp.getPendingSyncRequestCount() != 0 && snmp.getPendingSyncRequestCount() != 0) {
      try {
        Thread.sleep(250);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @BeforeEach
  public void setUp() throws Exception {
    task = new SnmpTrapSourceTask();
    Map<String, String> settings = SnmpTrapSourceConnectorConfigTest.settingsV3();
    task.start(settings);

    // Specify receiver
    String addr = String.format("127.0.0.1/%s", SnmpTrapSourceConnectorConfigTest.listeningPort);
    Address targetAddress = new UdpAddress(addr);

    this.validV3Targets = validUsers.stream()
        .map((u) -> createUserTarget(targetAddress, u.username, new byte[0]))
        .collect(Collectors.toList());

    v2Target = createCommunityTarget(targetAddress, "public");

    sendingSnmp = new Snmp(new DefaultUdpTransportMapping());
    setupMpv3(sendingSnmp, "generator");

    addUsms();

    assertNotSame(this.sendingSnmp.getLocalEngineID(), this.task.getSnmp().getLocalEngineID());
  }

  private void setupMpv3(Snmp snmp, String engineId) {
    SecurityModels securityModels = new SecurityModels() {
    };
    MPv3 mpv3 = (MPv3) snmp.getMessageDispatcher().getMessageProcessingModel(MPv3.ID);
    mpv3.setLocalEngineID(MPv3.createLocalEngineID(new OctetString(engineId)));
    USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(mpv3.getLocalEngineID()), 0);
    securityModels.addSecurityModel(usm);
    mpv3.setSecurityModels(securityModels);
  }

  @AfterEach
  public void tearDown() throws Exception {
    sendingSnmp.close();
    task.stop();
  }

  @Test
  public void shouldBufferV3Traps() throws InterruptedException, IOException {

    int i;

    for (i = 0; i < 20; i++) {

      int randomTarget = new Random().nextInt(this.validV3Targets.size());
      PDU trap = createV3Trap("1.2.3.4.5", "some string");
      sendingSnmp.send(trap, this.validV3Targets.get(randomTarget));
    }

    Thread.sleep(2500);
    assertEquals(i, task.getRecordBuffer().size(), "Sent traps should be equal to buffered records");
  }

  @Test
  public void shouldBufferLoadsOfV3() {

    final Random r = new Random();
    final int subset = 1_000;
    final int threads = 100;
    int loads = subset * threads;
    List<CompletableFuture<Void>> cfs = new ArrayList<>(threads);
    for (int j = threads; j > 0; j--) {
      final int ct = j;
      CompletableFuture<Void> cf = CompletableFuture.runAsync(
          () -> {
            for (int i = 0; i < subset; i++) {
              PDU trap = createV3Trap("1.2.3.4.5", "some string");
              try {
                int randomTarget = r.nextInt(this.validV3Targets.size());
                sendingSnmp.send(trap, this.validV3Targets.get(randomTarget));
                Thread.sleep(3);
              } catch (IOException e) {
                e.printStackTrace();
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            }
          }
      );

      cfs.add(cf);
    }
    cfs.forEach(CompletableFuture::join);
    // Consume all
    noPendingSnmpRequests(sendingSnmp);
    noPendingSnmpRequests(this.task.getSnmp());

    int bs = this.task.config.batchSize;
    List<SourceRecord> poll;
    int totals = 0;
    do {
      poll = this.task.poll();
      if (poll != null) {
        if (this.task.getRecordBuffer().isEmpty()) {
          assertTrue(bs >= poll.size());
        } else {
          assertEquals(bs, poll.size());
        }
        totals += poll.size();
      }
    } while (!this.task.getRecordBuffer().isEmpty());
    assertEquals(this.task.processedCount, BigInteger.valueOf(loads));
    assertEquals(totals, loads);

  }

  @Test
  public void shouldBufferLoadsOfV2() throws IOException, InterruptedException {
    final int subset = 1_000;
    final int threads = 100;
    int loads = subset * threads;
    List<CompletableFuture<Void>> cfs = new ArrayList<>(threads);
    for (int j = threads; j > 0; j--) {
      final int ct = j;
      CompletableFuture<Void> cf = CompletableFuture.runAsync(
          () -> {
            for (int i = 0; i < subset; i++) {
              PDU trap = createV2Trap("1.2.3.4.5", "some string");
              try {
                sendingSnmp.send(trap, v2Target);
                Thread.sleep(3);
              } catch (IOException e) {
                e.printStackTrace();
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
            }
          }
      );

      cfs.add(cf);
    }

    cfs.forEach(CompletableFuture::join);

    // Consume all
    noPendingSnmpRequests(sendingSnmp);
    noPendingSnmpRequests(this.task.getSnmp());

    int bs = this.task.config.batchSize;
    List<SourceRecord> poll;
    int totals = 0;

    do {
      poll = this.task.poll();
      if (poll != null) {
        if (this.task.getRecordBuffer().isEmpty()) {
          assertTrue(bs >= poll.size());
        } else {
          assertEquals(bs, poll.size());
        }
        totals += poll.size();
      }
    } while (!this.task.getRecordBuffer().isEmpty());

    assertEquals(this.task.processedCount, BigInteger.valueOf(loads));
    assertEquals(totals, loads);
  }
}