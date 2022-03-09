package com.github.jcustenborder.kafka.connect.snmp.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecordBufferTest {


  @Test
  public void testRecordBuffer() {
    String first = "first";
    RecordBuffer<String> rbs = new RecordBuffer<>();
    rbs.add(first);

    assertEquals(rbs.size(), 1);

    String last = "";
    for(int i = 0; i < 100; i++) {
      String s = java.util.UUID.randomUUID().toString();
      rbs.add(s);
      last = s;
    }

    assertEquals(rbs.size(), 101);
    assertEquals(rbs.poll(), first);
    assertEquals(rbs.size(), 100);
    assertFalse(rbs.isEmpty());

    String polled = "";
    while(rbs.size() != 0) {
      polled = rbs.poll();
    }

    assertEquals(polled, last);
    assertEquals(rbs.size(), 0);
    assertTrue(rbs.isEmpty());
  }
}