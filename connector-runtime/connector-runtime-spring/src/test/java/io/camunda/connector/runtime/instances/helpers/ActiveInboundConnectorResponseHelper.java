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
package io.camunda.connector.runtime.instances.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.inbound.controller.ActiveInboundConnectorResponse;
import io.camunda.connector.runtime.inbound.executable.ConnectorInstances;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public class ActiveInboundConnectorResponseHelper {
  private static final long activationTime1 = System.currentTimeMillis();
  private static final long activationTime2 = System.currentTimeMillis() + 1000;

  private static final ExecutableId IN_BOTH_RUNTIMES_WITH_UP_AND_DOWN =
      ExecutableId.fromDeduplicationId("inBothRuntimes_withUpAndDown");

  private static final ExecutableId IN_BOTH_RUNTIMES_WITH_UP_AND_UP =
      ExecutableId.fromDeduplicationId("inBothRuntimes_withUpAndUp");

  private static final ExecutableId IN_BOTH_RUNTIMES_WITH_UP_AND_UNKNOWN =
      ExecutableId.fromDeduplicationId("inBothRuntimes_withUpAndUnknown");

  private static final ExecutableId IN_BOTH_RUNTIMES_WITH_DOWN_AND_UNKNOWN =
      ExecutableId.fromDeduplicationId("inBothRuntimes_withDownAndUnknown");

  public static ActiveInboundConnectorResponse createResponse(
      ExecutableId executableId, Health health, long activationTimestamp) {
    return new ActiveInboundConnectorResponse(
        executableId,
        "type",
        "tenantId",
        List.of(),
        Map.of("dataKey", "dataValue"),
        health,
        activationTimestamp);
  }

  public static Stream<Arguments> getConnectorInstancesWithExpectedResult() {
    return Stream.of(
        // UP + DOWN = DOWN
        Arguments.of(
            createResponse(IN_BOTH_RUNTIMES_WITH_UP_AND_DOWN, Health.up(), activationTime1),
            createResponse(IN_BOTH_RUNTIMES_WITH_UP_AND_DOWN, Health.down(), activationTime2),
            createResponse(IN_BOTH_RUNTIMES_WITH_UP_AND_DOWN, Health.down(), activationTime2)),
        // UP + UP = UP
        Arguments.of(
            createResponse(IN_BOTH_RUNTIMES_WITH_UP_AND_UP, Health.up(), activationTime1),
            createResponse(IN_BOTH_RUNTIMES_WITH_UP_AND_UP, Health.up(), activationTime2),
            createResponse(IN_BOTH_RUNTIMES_WITH_UP_AND_UP, Health.up(), activationTime1)),
        // UP + UNKNOWN = UNKNOWN
        Arguments.of(
            createResponse(IN_BOTH_RUNTIMES_WITH_UP_AND_UNKNOWN, Health.up(), activationTime1),
            createResponse(IN_BOTH_RUNTIMES_WITH_UP_AND_UNKNOWN, Health.unknown(), activationTime2),
            createResponse(
                IN_BOTH_RUNTIMES_WITH_UP_AND_UNKNOWN, Health.unknown(), activationTime2)),
        // DOWN + UNKNOWN = DOWN
        Arguments.of(
            createResponse(IN_BOTH_RUNTIMES_WITH_DOWN_AND_UNKNOWN, Health.down(), activationTime1),
            createResponse(
                IN_BOTH_RUNTIMES_WITH_DOWN_AND_UNKNOWN, Health.unknown(), activationTime2),
            createResponse(
                IN_BOTH_RUNTIMES_WITH_DOWN_AND_UNKNOWN, Health.down(), activationTime1)));
  }

  public static void assertExpectedResponse(
      ActiveInboundConnectorResponse expectedResult, ActiveInboundConnectorResponse actualResult) {
    assertThat(actualResult.executableId()).isEqualTo(expectedResult.executableId());
    assertThat(actualResult.type()).isEqualTo(expectedResult.type());
    assertThat(actualResult.tenantId()).isEqualTo(expectedResult.tenantId());
    assertThat(actualResult.data()).isEqualTo(expectedResult.data());
    assertThat(actualResult.health().getStatus()).isEqualTo(expectedResult.health().getStatus());
    assertThat(actualResult.health().getDetails()).isEqualTo(expectedResult.health().getDetails());
    assertThat(actualResult.activationTimestamp()).isEqualTo(expectedResult.activationTimestamp());
  }

  public static void assertExpectedResponse(
      ActiveInboundConnectorResponse expectedResult,
      ConnectorInstances reducedResponse,
      ConnectorInstances connectorInstances1) {
    assertThat(reducedResponse.connectorId()).isEqualTo(connectorInstances1.connectorId());
    assertThat(reducedResponse.connectorName()).isEqualTo(connectorInstances1.connectorName());
    assertThat(reducedResponse.instances()).hasSize(3);

    assertThat(
            reducedResponse.instances().stream()
                .filter(
                    instance ->
                        instance
                                .executableId()
                                .equals(ExecutableId.fromDeduplicationId("onlyInRuntime1"))
                            || instance
                                .executableId()
                                .equals(ExecutableId.fromDeduplicationId("onlyInRuntime2")))
                .count())
        .isEqualTo(2);

    var commonInstance =
        reducedResponse.instances().stream()
            .filter(instance -> instance.executableId().equals(expectedResult.executableId()))
            .findFirst()
            .get();

    assertExpectedResponse(expectedResult, commonInstance);
  }
}
