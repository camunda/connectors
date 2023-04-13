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

import io.camunda.connector.impl.inbound.correlation.MessageCorrelationPoint;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class InboundConnectorPropertiesTest {

  @Test
  void getPropertiesAsObjectMap_shouldHandleNestedProperties() {
    // given
    InboundConnectorProperties properties =
        new InboundConnectorProperties(
            new MessageCorrelationPoint(""),
            Map.of(
                "foo.bar", "alpha",
                "foo.foo", "beta",
                "bar", "gamma"),
            "process1",
            0,
            0);

    // when
    Map<String, Object> objectMap = properties.getPropertiesAsObjectMap();

    // then
    var template = Map.of("bar", "gamma", "foo", Map.of("foo", "beta", "bar", "alpha"));

    Assertions.assertThat(objectMap).containsExactlyInAnyOrderEntriesOf(template);
  }
}
