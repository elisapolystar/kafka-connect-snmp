package com.github.jcustenborder.kafka.connect.snmp.monitor;

public interface SnmpMetricsMBean {
  int getProcessed();
  int getToProcess();
  int getPolled();
}
