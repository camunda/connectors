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
package io.camunda.connector.runtime.instances.reducer;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.inbound.controller.ActiveInboundConnectorResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ActiveInboundConnectorResponseReducerTest {
  ActiveInboundConnectorResponseReducer reducer = new ActiveInboundConnectorResponseReducer();

  private static Stream<Arguments> getResponsesWithExpectedResult() {
    var activationTime1 = System.currentTimeMillis();
    var activationTime2 = System.currentTimeMillis() + 1000;
    return Stream.of(
        // UP + DOWN = DOWN
        Arguments.of(
            activationTime1,
            Health.down(new IllegalArgumentException("Test")),
            activationTime2,
            Health.up(),
            Health.down(new IllegalArgumentException("Test")),
            activationTime1),
        // UP + UNKNOWN = UNKNOWN
        Arguments.of(
            activationTime1,
            Health.unknown("key", "value"),
            activationTime2,
            Health.up(),
            Health.unknown("key", "value"),
            activationTime1),
        // UNKNOWN + DOWN = DOWN
        Arguments.of(
            activationTime1,
            Health.down(new IllegalArgumentException("Test")),
            activationTime2,
            Health.unknown("key", "value"),
            Health.down(new IllegalArgumentException("Test")),
            activationTime1),
        // UNKNOWN + UP = UNKNOWN
        Arguments.of(
            activationTime1,
            Health.up(),
            activationTime2,
            Health.unknown("key", "value"),
            Health.unknown("key", "value"),
            activationTime2));
  }

  @ParameterizedTest
  @MethodSource("getResponsesWithExpectedResult")
  public void shouldReduce_whenExpectedResponse(
      long activationTime1,
      Health health1,
      long activationTime2,
      Health health2,
      Health expectedHealth,
      long expectedActivationTime) {
    // given
    ActiveInboundConnectorResponse response1 =
        new ActiveInboundConnectorResponse(
            ExecutableId.fromDeduplicationId("executableId"),
            "type",
            "tenantId",
            List.of(),
            Map.of("dataKey", "dataValue"),
            health1,
            activationTime1);
    ActiveInboundConnectorResponse response2 =
        new ActiveInboundConnectorResponse(
            ExecutableId.fromDeduplicationId("executableId"),
            "type",
            "tenantId",
            List.of(),
            Map.of("dataKey", "dataValue"),
            health2,
            activationTime2);

    // when
    ActiveInboundConnectorResponse reducedResponse = reducer.reduce(response1, response2);

    // then
    assertThat(reducedResponse.health()).isEqualTo(expectedHealth);
    assertThat(reducedResponse.activationTimestamp()).isEqualTo(expectedActivationTime);
    assertThat(reducedResponse.data()).isEqualTo(response1.data());
    assertThat(reducedResponse.elements()).isEqualTo(response1.elements());
    assertThat(reducedResponse.executableId()).isEqualTo(response1.executableId());
    assertThat(reducedResponse.type()).isEqualTo(response1.type());
    assertThat(reducedResponse.tenantId()).isEqualTo(response1.tenantId());
  }
}
