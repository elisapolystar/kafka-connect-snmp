/**
 * Copyright © 2017 Jeremy Custenborder (jcustenborder@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jcustenborder.kafka.connect.snmp;


import com.github.jcustenborder.kafka.connect.utils.config.MarkdownFormatter;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SnmpTrapSourceConnectorConfigTest {

  public static String listeningPort = "11111";
  public static int batchSize = 10;
  public static Map<String, String> settings() {
    return ImmutableMap.of(
        SnmpTrapSourceConnectorConfig.LISTEN_PORT_CONF, listeningPort,
        SnmpTrapSourceConnectorConfig.TOPIC_CONF, "testing",
        SnmpTrapSourceConnectorConfig.BATCH_SIZE_CONF, String.format("%d", batchSize)
    );
  }


  @Test
  public void doc() {

    System.out.println(
        MarkdownFormatter.toMarkdown(SnmpTrapSourceConnectorConfig.conf())
    );
  }

  @Test
  public void shouldConvertLists() {
    Map<String, String> m = Map.of(
            SnmpTrapSourceConnectorConfig.TOPIC_CONF, "topic",
            SnmpTrapSourceConnectorConfig.AUTHENTICATION_PROTOCOLS, "MD5,SHA",
            SnmpTrapSourceConnectorConfig.PRIVACY_PROTOCOLS, "DES3"
    );
    SnmpTrapSourceConnectorConfig c = new SnmpTrapSourceConnectorConfig(m);
    assertEquals(Set.of(AuthenticationProtocol.MD5, AuthenticationProtocol.SHA), c.authenticationProtocols);
    assertEquals(Set.of(PrivacyProtocol.DES3), c.privacyProtocols);


    m = Map.of(
            SnmpTrapSourceConnectorConfig.TOPIC_CONF, "topic",
            SnmpTrapSourceConnectorConfig.AUTHENTICATION_PROTOCOLS, "MD5"
    );
    c = new SnmpTrapSourceConnectorConfig(m);
    assertEquals(Set.of(AuthenticationProtocol.MD5), c.authenticationProtocols);

  }

  @Test
  public void shouldNotAcceptInvalidListValues() {
    Map<String, String> m = Map.of(
            SnmpTrapSourceConnectorConfig.TOPIC_CONF, "topic",
            SnmpTrapSourceConnectorConfig.AUTHENTICATION_PROTOCOLS, "aaa,bbb",
            SnmpTrapSourceConnectorConfig.PRIVACY_PROTOCOLS, "ccc"
    );
    assertThrows(org.apache.kafka.common.config.ConfigException.class, () -> new SnmpTrapSourceConnectorConfig(m));

  }
}