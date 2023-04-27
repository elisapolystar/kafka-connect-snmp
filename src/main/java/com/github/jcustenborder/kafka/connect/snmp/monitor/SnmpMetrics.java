/**
 * Copyright © 2023 Elisa Oyj
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
package com.github.jcustenborder.kafka.connect.snmp.monitor;

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
