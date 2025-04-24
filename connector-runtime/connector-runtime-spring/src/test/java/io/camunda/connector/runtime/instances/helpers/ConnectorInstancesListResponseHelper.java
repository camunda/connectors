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

public class ConnectorInstancesListResponseHelper {
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

  public static Stream<Arguments> getConnectorInstancesListsWithExpectedResult() {
    return Stream.of(
        // UP + DOWN = DOWN
        Arguments.of(
            // Runtime 1
            List.of(
                new ConnectorInstances(
                    "webhook",
                    "Webhook connector",
                    List.of(
                        createResponse(
                            IN_BOTH_RUNTIMES_WITH_UP_AND_DOWN, Health.up(), activationTime1),
                        createResponse(
                            IN_BOTH_RUNTIMES_WITH_UP_AND_UP, Health.up(), activationTime1),
                        createResponse(
                            IN_BOTH_RUNTIMES_WITH_UP_AND_UNKNOWN, Health.up(), activationTime1),
                        createResponse(
                            IN_BOTH_RUNTIMES_WITH_DOWN_AND_UNKNOWN, Health.down(), activationTime1),
                        createResponse(
                            ExecutableId.fromDeduplicationId("onlyInRuntime1"),
                            Health.down(),
                            activationTime2))),
                new ConnectorInstances(
                    "kafka",
                    "Kafka connector only in Runtime 1",
                    List.of(
                        createResponse(
                            ExecutableId.fromDeduplicationId("onlyInRuntime1"),
                            Health.up(),
                            activationTime1)))),
            // Runtime 2
            List.of(
                new ConnectorInstances(
                    "webhook",
                    "Webhook connector",
                    List.of(
                        createResponse(
                            IN_BOTH_RUNTIMES_WITH_UP_AND_DOWN, Health.down(), activationTime2),
                        createResponse(
                            IN_BOTH_RUNTIMES_WITH_UP_AND_UP, Health.up(), activationTime2),
                        createResponse(
                            IN_BOTH_RUNTIMES_WITH_UP_AND_UNKNOWN,
                            Health.unknown(),
                            activationTime2),
                        createResponse(
                            IN_BOTH_RUNTIMES_WITH_DOWN_AND_UNKNOWN,
                            Health.unknown(),
                            activationTime2),
                        createResponse(
                            ExecutableId.fromDeduplicationId("onlyInRuntime2"),
                            Health.up(),
                            activationTime1))),
                new ConnectorInstances(
                    "other",
                    "Other connector only in Runtime 2",
                    List.of(
                        createResponse(
                            ExecutableId.fromDeduplicationId("onlyInRuntime2"),
                            Health.up(),
                            activationTime1)))),
            // Expected Result
            List.of(
                new ConnectorInstances(
                    "webhook",
                    "Webhook connector",
                    List.of(
                        createResponse(
                            IN_BOTH_RUNTIMES_WITH_UP_AND_DOWN, Health.down(), activationTime2),
                        createResponse(
                            IN_BOTH_RUNTIMES_WITH_UP_AND_UP, Health.up(), activationTime1),
                        createResponse(
                            IN_BOTH_RUNTIMES_WITH_UP_AND_UNKNOWN,
                            Health.unknown(),
                            activationTime2),
                        createResponse(
                            IN_BOTH_RUNTIMES_WITH_DOWN_AND_UNKNOWN, Health.down(), activationTime1),
                        createResponse(
                            ExecutableId.fromDeduplicationId("onlyInRuntime2"),
                            Health.up(),
                            activationTime1),
                        createResponse(
                            ExecutableId.fromDeduplicationId("onlyInRuntime1"),
                            Health.down(),
                            activationTime2))),
                new ConnectorInstances(
                    "kafka",
                    "Kafka connector only in Runtime 1",
                    List.of(
                        createResponse(
                            ExecutableId.fromDeduplicationId("onlyInRuntime1"),
                            Health.up(),
                            activationTime1))),
                new ConnectorInstances(
                    "other",
                    "Other connector only in Runtime 2",
                    List.of(
                        createResponse(
                            ExecutableId.fromDeduplicationId("onlyInRuntime2"),
                            Health.up(),
                            activationTime1))))));
  }

  public static void assertResponse(
      List<ConnectorInstances> actual, List<ConnectorInstances> expected) {
    assertThat(actual).hasSameSizeAs(expected);
    for (ConnectorInstances actualInstance : actual) {
      ConnectorInstances expectedInstance =
          expected.stream()
              .filter(instance -> instance.connectorId().equals(actualInstance.connectorId()))
              .findFirst()
              .orElseThrow();
      assertThat(actualInstance.connectorId()).isEqualTo(expectedInstance.connectorId());
      assertThat(actualInstance.connectorName()).isEqualTo(expectedInstance.connectorName());
      assertThat(actualInstance.instances()).hasSameSizeAs(expectedInstance.instances());
      assertThat(actualInstance.instances())
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactlyInAnyOrderElementsOf(expectedInstance.instances());
    }
  }
}
