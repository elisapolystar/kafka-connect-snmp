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

import java.util.LinkedList;
import java.util.List;

public class RecordBuffer<T> {
  private LinkedList<T> buffer;
  private final Object lock;

  public RecordBuffer() {
    buffer = new LinkedList<>();
    lock = new Object();
  }

  public void add(T element) {
    synchronized (lock) {
      buffer.add(element);
    }
  }

  public void addAll(T[] elements) {
    synchronized (lock) {
      for (T element : elements) {
        buffer.add(element);
      }
    }
  }
  public int size() {
    synchronized (lock) {
      return buffer.size();
    }
  }

  public boolean isEmpty() {
    synchronized (lock) {
      return buffer.isEmpty();
    }
  }

  public List<T> drain(int numRecords) {
    LinkedList<T> result = new LinkedList<>();
    synchronized (lock) {
      while (numRecords > 0 && !buffer.isEmpty()) {
        result.add(buffer.poll());
        numRecords--;
      }
    }
    return result;
  }
}
