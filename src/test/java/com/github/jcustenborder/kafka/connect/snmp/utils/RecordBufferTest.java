package com.github.jcustenborder.kafka.connect.snmp.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
    assertEquals(rbs.drain(1).get(0), first);
    assertEquals(rbs.size(), 100);
    assertFalse(rbs.isEmpty());

    String polled = "";
    while(rbs.size() != 0) {
      polled = rbs.drain(1).get(0);
    }

    assertEquals(polled, last);
    assertEquals(rbs.size(), 0);
    assertTrue(rbs.isEmpty());
  }

  @Test
  public void testMany() {
    RecordBuffer<String> rbs = new RecordBuffer<>();
    String last = "";
    int cnt = 1_000_000;
    for(int i = 0; i < cnt; i++) {
      String s = java.util.UUID.randomUUID().toString();
      rbs.add(s);
      last = s;
    }

    assertEquals(rbs.size(), cnt);

    ArrayList<String> res = new ArrayList<>();
    int i = 0;
    int bs = 10_000;
    while(!rbs.isEmpty()) {
      i++;
      res.addAll(rbs.drain(bs));
    }

    assertEquals(i, 100);
    assertEquals(0, rbs.size());
    assertEquals(res.size(), cnt);
    assertEquals(res.get(res.size() - 1), last);
  }

  @Test
  public void testEmtpy() {
    RecordBuffer<String> rbs = new RecordBuffer<>();
    List<String> drain = rbs.drain(100);
    assertTrue(rbs.isEmpty());
    assertTrue(drain.isEmpty());
  }

}