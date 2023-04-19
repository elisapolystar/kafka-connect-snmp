package com.github.jcustenborder.kafka.connect.snmp.utils;

import org.snmp4j.event.CounterEvent;
import org.snmp4j.event.CounterListener;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

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
