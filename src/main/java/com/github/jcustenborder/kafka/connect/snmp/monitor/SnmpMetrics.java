package com.github.jcustenborder.kafka.connect.snmp.monitor;

import org.apache.kafka.common.Metric;
import org.apache.kafka.common.metrics.JmxReporter;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.metrics.MetricsReporter;

import java.util.List;
import java.util.Map;


public class SnmpMetrics implements SnmpMetricsMBean {
  final Counter processed;
  final Counter toProcess;
  final Counter polled;

  public SnmpMetrics() {
    processed = new Counter();
    toProcess = new Counter();
    polled = new Counter();
  }

  public void incrementProcessed() {
    this.processed.increment();
  }

  public void incrementToProcess() {
    this.toProcess.increment();
  }

  public void addPolled(int n) {
    this.polled.add(n);
  }

  @Override
  public int getProcessed() {
    return processed.get();
  }

  @Override
  public int getToProcess() {
    return toProcess.get();
  }

  @Override
  public int getPolled() {
    return polled.get();
  }
}
