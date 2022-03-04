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
package com.github.jcustenborder.kafka.connect.snmp.utils;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class to handle constant time size check and concurrent deque ops
 *
 * @param <T>
 */
public class RecordBuffer<T> {

  private final ConcurrentLinkedDeque<T> queu;
  private AtomicInteger size = new AtomicInteger(0);

  public RecordBuffer() {
    this.queu = new ConcurrentLinkedDeque<>();
  }

  public void add(T ele) {
    this.queu.add(ele);
    size.incrementAndGet();
  }

  public T poll() {
    T ele = this.queu.poll();
    if (ele != null) {
      size.decrementAndGet();
    }
    return ele;
  }

  public int size() {
    return this.size.get();
  }

  public boolean isEmpty() {
    return this.queu.isEmpty();
  }

  public void clear() {
    this.queu.clear();
    this.size.set(0);
  }
}
