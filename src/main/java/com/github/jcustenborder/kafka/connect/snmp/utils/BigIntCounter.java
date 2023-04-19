package com.github.jcustenborder.kafka.connect.snmp.utils;
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
import org.snmp4j.event.CounterEvent;
import org.snmp4j.event.CounterListener;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * Counter for SNMP
 */
public class BigIntCounter implements CounterListener {

  Map<String, BigInteger> counters;

  public BigIntCounter() {
    counters = new HashMap<>();
  }

  @Override
  public void incrementCounter(CounterEvent event) {
    String k = event.getOid().toDottedString();
    if(counters.containsKey(k)) {
      BigInteger bi = counters.get(k);
      counters.put(k, bi.add(BigInteger.ONE));
    } else {
      counters.put(k, new BigInteger("1"));
    }
  }
}
