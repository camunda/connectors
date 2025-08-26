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
package io.camunda.connector.runtime.inbound;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import io.camunda.connector.api.inbound.*;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.inbound.controller.ActiveInboundConnectorResponse;
import io.camunda.connector.runtime.inbound.executable.ConnectorInstances;
import io.camunda.connector.runtime.instances.InstanceAwareModel;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class InboundInstancesRestControllerMultiInstancesTest extends BaseMultiInstancesTest {

  @Test
  public void shouldReturnConnectorInstances() {
    ResponseEntity<List<ConnectorInstances>> response =
        restTemplate.exchange(
            "http://localhost:" + port1 + "/inbound-instances",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});

    List<ConnectorInstances> instance = response.getBody();
    assertEquals(2, instance.size());
    var instance1 = instance.get(0);
    assertEquals(TYPE_1, instance1.connectorId());
    assertEquals("Webhook", instance1.connectorName());
    assertEquals(3, instance1.instances().size());
    var executableIds =
        instance1.instances().stream().map(ActiveInboundConnectorResponse::executableId).toList();
    assertThat(
        executableIds, containsInAnyOrder(RANDOM_ID_1, ONLY_IN_RUNTIME_1_ID, ONLY_IN_RUNTIME_2_ID));
    ActiveInboundConnectorResponse activeInboundConnectorResponse =
        instance1.instances().stream()
            .filter(r -> r.executableId().equals(RANDOM_ID_1))
            .findFirst()
            .get();
    assertEquals(
        Health.down(new IllegalArgumentException("Test error message")),
        activeInboundConnectorResponse.health());
    assertEquals("ProcessA", activeInboundConnectorResponse.elements().getFirst().bpmnProcessId());

    var instance2 = instance.get(1);
    assertEquals(TYPE_2, instance2.connectorId());
    assertEquals("AnotherType", instance2.connectorName());
    assertEquals(2, instance2.instances().size());
    assertEquals(RANDOM_ID_2, instance2.instances().get(0).executableId());
    assertEquals(
        Health.unknown("Test unknown key", "Test unknown value"),
        instance2.instances().get(0).health());
    assertEquals("ProcessB", instance2.instances().get(0).elements().getFirst().bpmnProcessId());
    assertEquals(RANDOM_ID_3, instance2.instances().get(1).executableId());
    assertEquals(
        Health.unknown("Test unknown key", "Test unknown value"),
        instance2.instances().get(1).health());
    assertEquals("ProcessC", instance2.instances().get(1).elements().getFirst().bpmnProcessId());
  }

  @Test
  public void shouldReturn404_whenUnknownConnectorType() {
    var response =
        restTemplate.getForEntity(
            "http://localhost:" + port1 + "/inbound-instances/UNKNOWN-ID", String.class);
    assertThat(
        response.getBody(),
        containsString("Data of type 'ConnectorInstances' with id 'UNKNOWN-ID' not found"));
    assertThat(404, equalTo(response.getStatusCode().value()));
  }

  @Test
  public void shouldReturnSingleConnectorInstance() {
    ResponseEntity<ConnectorInstances> response =
        restTemplate.exchange(
            "http://localhost:" + port1 + "/inbound-instances/" + TYPE_1,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});

    var instance = response.getBody();
    assertEquals(TYPE_1, instance.connectorId());
    assertEquals("Webhook", instance.connectorName());
    assertEquals(3, instance.instances().size());
    var executableIds =
        instance.instances().stream().map(ActiveInboundConnectorResponse::executableId).toList();
    assertThat(
        executableIds, containsInAnyOrder(RANDOM_ID_1, ONLY_IN_RUNTIME_1_ID, ONLY_IN_RUNTIME_2_ID));
    ActiveInboundConnectorResponse activeInboundConnectorResponse =
        instance.instances().stream()
            .filter(r -> r.executableId().equals(RANDOM_ID_1))
            .findFirst()
            .get();
    assertEquals(
        Health.down(new IllegalArgumentException("Test error message")),
        activeInboundConnectorResponse.health());
    assertEquals("ProcessA", activeInboundConnectorResponse.elements().getFirst().bpmnProcessId());
  }

  @Test
  public void shouldReturn404_whenUnknownExecutableId() {
    var response =
        restTemplate.getForEntity(
            "http://localhost:"
                + port1
                + "/inbound-instances/"
                + TYPE_1
                + "/executables/UNKNOWN-ID",
            String.class);
    assertThat(
        response.getBody(),
        containsString(
            "Data of type 'ActiveInboundConnectorResponse' with id 'UNKNOWN-ID' not found"));
    assertThat(404, equalTo(response.getStatusCode().value()));
  }

  @Test
  public void shouldReturn404_whenUnknownConnectorTypeAndValidExecutableId() {
    var response =
        restTemplate.getForEntity(
            "http://localhost:"
                + port1
                + "/inbound-instances/UNKNOWN-ID/executables/"
                + RANDOM_ID_1.getId(),
            String.class);
    assertThat(
        response.getBody(),
        containsString(
            "Data of type 'ActiveInboundConnectorResponse' with id 'e379ef68540767f31108eb2fa581814616895690cc92bc5e955e1001743e49b9' not found"));
    assertThat(404, equalTo(response.getStatusCode().value()));
  }

  private static Stream<Arguments> provideExecutableIds() {
    return Stream.of(
        Arguments.of(RANDOM_ID_1),
        Arguments.of(ONLY_IN_RUNTIME_1_ID),
        Arguments.of(ONLY_IN_RUNTIME_2_ID));
  }

  @ParameterizedTest
  @MethodSource("provideExecutableIds")
  public void shouldReturnSingleExecutable(ExecutableId executableId) {
    var response =
        restTemplate.getForEntity(
            "http://localhost:"
                + port1
                + "/inbound-instances/"
                + TYPE_1
                + "/executables/"
                + executableId.getId(),
            ActiveInboundConnectorResponse.class);
    var executable = response.getBody();
    assertEquals(executableId, executable.executableId());
    assertEquals("ProcessA", executable.elements().getFirst().bpmnProcessId());
  }

  @Test
  public void shouldReturnEmptyActivityLogs_whenNoLogs() {
    ResponseEntity<List<InstanceAwareModel.InstanceAwareActivity>> response =
        restTemplate.exchange(
            "http://localhost:"
                + port1
                + "/inbound-instances/"
                + TYPE_1
                + "/executables/"
                + RANDOM_ID_1.getId()
                + "/logs",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    var logs = response.getBody();
    assertTrue(logs.isEmpty());
  }

  @Test
  public void shouldReturnActivityLogs_whenTypeProvided() {
    ResponseEntity<List<InstanceAwareModel.InstanceAwareActivity>> response =
        restTemplate.exchange(
            "http://localhost:"
                + port1
                + "/inbound-instances/"
                + TYPE_2
                + "/executables/"
                + RANDOM_ID_3.getId()
                + "/logs",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    var logs = response.getBody();
    assertEquals(4, logs.size());
    assertThat(
        logs,
        containsInAnyOrder(
            new InstanceAwareModel.InstanceAwareActivity(
                RUNTIME1_ACTIVITY1.severity(),
                RUNTIME1_ACTIVITY1.tag(),
                RUNTIME1_ACTIVITY1.timestamp().withOffsetSameInstant(ZoneOffset.UTC),
                RUNTIME1_ACTIVITY1.message(),
                "instance1"),
            new InstanceAwareModel.InstanceAwareActivity(
                RUNTIME1_ACTIVITY2.severity(),
                RUNTIME1_ACTIVITY2.tag(),
                RUNTIME1_ACTIVITY2.timestamp().withOffsetSameInstant(ZoneOffset.UTC),
                RUNTIME1_ACTIVITY2.message(),
                "instance1"),
            new InstanceAwareModel.InstanceAwareActivity(
                RUNTIME2_ACTIVITY1.severity(),
                RUNTIME2_ACTIVITY1.tag(),
                RUNTIME2_ACTIVITY1.timestamp().withOffsetSameInstant(ZoneOffset.UTC),
                RUNTIME2_ACTIVITY1.message(),
                "instance2"),
            new InstanceAwareModel.InstanceAwareActivity(
                RUNTIME2_ACTIVITY2.severity(),
                RUNTIME2_ACTIVITY2.tag(),
                RUNTIME2_ACTIVITY2.timestamp().withOffsetSameInstant(ZoneOffset.UTC),
                RUNTIME2_ACTIVITY2.message(),
                "instance2")));
  }

  @Test
  public void shouldReturnHealth_whenBothInstancesHaveExecutable() {
    ResponseEntity<List<InstanceAwareModel.InstanceAwareHealth>> response =
        restTemplate.exchange(
            "http://localhost:"
                + port1
                + "/inbound-instances/"
                + TYPE_2
                + "/executables/"
                + RANDOM_ID_3.getId()
                + "/health",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    var logs = response.getBody();
    assertEquals(2, logs.size());
    assertThat(
        logs,
        containsInAnyOrder(
            new InstanceAwareModel.InstanceAwareHealth(
                Health.Status.UNKNOWN,
                null,
                Map.of("Test unknown key", "Test unknown value"),
                "instance1"),
            new InstanceAwareModel.InstanceAwareHealth(Health.Status.UP, null, null, "instance2")));
  }

  @Test
  public void shouldReturnHealth_whenOnly1InstanceHasExecutable() {
    ResponseEntity<List<InstanceAwareModel.InstanceAwareHealth>> response =
        restTemplate.exchange(
            "http://localhost:"
                + port1
                + "/inbound-instances/"
                + TYPE_1
                + "/executables/"
                + ONLY_IN_RUNTIME_1_ID.getId()
                + "/health",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    var logs = response.getBody();
    assertEquals(1, logs.size());
    assertThat(
        logs.getFirst(),
        equalTo(
            new InstanceAwareModel.InstanceAwareHealth(Health.Status.UP, null, null, "instance1")));
  }
}
