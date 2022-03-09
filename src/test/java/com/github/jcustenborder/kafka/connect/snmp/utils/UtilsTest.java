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

import com.github.jcustenborder.kafka.connect.snmp.utils.Utils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class UtilsTest {

  @Test
  public void testNoneNull1() {
    Object[] objects = {null, new Object(), null};
    assertFalse(Utils.noneNull(objects));
  }

  @Test
  public void testNoneNull2() {
    Object[] objects = {null, null, null};
    assertFalse(Utils.noneNull(objects));
  }

  @Test
  public void testNoneNull3() {
    Object[] objects = {new Object(), new Object(), new ArrayList<>()};
    assertTrue(Utils.noneNull(objects));
  }
}