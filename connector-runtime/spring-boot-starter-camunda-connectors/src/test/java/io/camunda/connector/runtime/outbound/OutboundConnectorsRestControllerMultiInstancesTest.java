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
package io.camunda.connector.runtime.outbound;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.connector.runtime.outbound.controller.OutboundConnectorResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class OutboundConnectorsRestControllerMultiInstancesTest extends BaseOutboundMultiInstancesTest {

  @Test
  void shouldReturnConnectorsFromAllNodes() {
    ResponseEntity<List<OutboundConnectorResponse>> response =
        restTemplate.exchange(
            "http://localhost:" + port1 + "/outbound",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});

    var connectors = response.getBody();
    // instance1: 3 (2 enabled + 1 disabled), instance2: 3 (all enabled)
    assertEquals(6, connectors.size());

    var instance1Connectors =
        connectors.stream().filter(c -> "instance1".equals(c.runtimeId())).toList();
    var instance2Connectors =
        connectors.stream().filter(c -> "instance2".equals(c.runtimeId())).toList();

    assertEquals(3, instance1Connectors.size());
    assertEquals(3, instance2Connectors.size());

    // instance1 has a disabled connector
    var disabledInInstance1 =
        instance1Connectors.stream()
            .filter(c -> TYPE_DISABLED_IN_INSTANCE_1.equals(c.type()))
            .findFirst()
            .orElseThrow();
    assertFalse(disabledInInstance1.enabled());

    // all other connectors on instance1 are enabled
    instance1Connectors.stream()
        .filter(c -> !TYPE_DISABLED_IN_INSTANCE_1.equals(c.type()))
        .forEach(c -> assertTrue(c.enabled()));

    // all connectors on instance2 are enabled
    instance2Connectors.forEach(c -> assertTrue(c.enabled()));
  }

  @Test
  void shouldReturnConnectorByType_fromAllNodes() {
    ResponseEntity<List<OutboundConnectorResponse>> response =
        restTemplate.exchange(
            "http://localhost:" + port1 + "/outbound/" + TYPE_1,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});

    var connectors = response.getBody();
    // TYPE_1 is registered on both nodes → one entry per node, both enabled
    assertEquals(2, connectors.size());
    assertTrue(connectors.stream().anyMatch(c -> "instance1".equals(c.runtimeId())));
    assertTrue(connectors.stream().anyMatch(c -> "instance2".equals(c.runtimeId())));
    connectors.forEach(c -> assertEquals(TYPE_1, c.type()));
    connectors.forEach(c -> assertTrue(c.enabled()));
  }

  @Test
  void shouldReturnDisabledConnector_fromNodeWhereItIsDisabled() {
    ResponseEntity<List<OutboundConnectorResponse>> response =
        restTemplate.exchange(
            "http://localhost:" + port1 + "/outbound/" + TYPE_DISABLED_IN_INSTANCE_1,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});

    var connectors = response.getBody();
    // Only instance1 has this connector, and it is disabled there
    assertEquals(1, connectors.size());
    assertEquals("instance1", connectors.getFirst().runtimeId());
    assertFalse(connectors.getFirst().enabled());
  }

  @Test
  void shouldReturnConnectorOnlyInOneNode_whenTypeFilteredToThatNode() {
    ResponseEntity<List<OutboundConnectorResponse>> response =
        restTemplate.exchange(
            "http://localhost:" + port1 + "/outbound/" + TYPE_ONLY_IN_INSTANCE_2,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});

    var connectors = response.getBody();
    // Only instance2 has this connector, and it is enabled
    assertEquals(1, connectors.size());
    assertEquals("instance2", connectors.getFirst().runtimeId());
    assertTrue(connectors.getFirst().enabled());
  }

  @Test
  void shouldReturn404_whenTypeNotFoundOnAnyNode() {
    var response =
        restTemplate.getForEntity(
            "http://localhost:" + port1 + "/outbound/unknown-type", String.class);

    assertThat(404, equalTo(response.getStatusCode().value()));
    assertThat(
        response.getBody(),
        containsString(
            "Data of type 'OutboundConnectorResponse' with id 'unknown-type' not found"));
  }
}
