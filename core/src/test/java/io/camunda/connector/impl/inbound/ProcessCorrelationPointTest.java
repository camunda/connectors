/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.impl.inbound;

import io.camunda.connector.api.inbound.ProcessCorrelationPoint;
import io.camunda.connector.impl.inbound.correlation.MessageCorrelationPoint;
import io.camunda.connector.impl.inbound.correlation.StartEventCorrelationPoint;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ProcessCorrelationPointTest {

  @Test
  void shouldBeSortableByProcessVersion() {
    // given
    ProcessCorrelationPoint p1 = new StartEventCorrelationPoint(0, "process1", 0);
    ProcessCorrelationPoint p2 = new StartEventCorrelationPoint(2, "process1", 1);
    ProcessCorrelationPoint p3 = new StartEventCorrelationPoint(1, "process2", 0);
    ProcessCorrelationPoint p4 = new MessageCorrelationPoint("jobCompleted", "=key");
    ProcessCorrelationPoint p5 = new MessageCorrelationPoint("jobCompleted1", "=key");
    ProcessCorrelationPoint p6 = new MessageCorrelationPoint("jobCompleted1", "=key1");

    // when
    List<ProcessCorrelationPoint> sortedPoints = List.of(p6, p5, p1, p2, p3, p4);
    sortedPoints = sortedPoints.stream().sorted().collect(Collectors.toList());

    // then
    Iterator<ProcessCorrelationPoint> iter = sortedPoints.iterator();
    // points are sorted in the expected order
    Assertions.assertThat(iter.next()).isSameAs(p1);
    Assertions.assertThat(iter.next()).isSameAs(p2);
    Assertions.assertThat(iter.next()).isSameAs(p3);
    Assertions.assertThat(iter.next()).isSameAs(p4);
    Assertions.assertThat(iter.next()).isSameAs(p5);
    Assertions.assertThat(iter.next()).isSameAs(p6);
    Assertions.assertThat(iter.hasNext()).isFalse();
  }
}
