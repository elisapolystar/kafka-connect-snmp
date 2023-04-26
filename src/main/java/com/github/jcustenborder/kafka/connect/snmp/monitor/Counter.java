package com.github.jcustenborder.kafka.connect.snmp.monitor;

import java.util.concurrent.atomic.AtomicInteger;

public class Counter {

  private final AtomicInteger count = new AtomicInteger(0);

  public void increment() {
    count.incrementAndGet();
  }

  public void add(int n) {
    count.addAndGet(n);
  }
  public int get() {
    return count.get();
  }
}
