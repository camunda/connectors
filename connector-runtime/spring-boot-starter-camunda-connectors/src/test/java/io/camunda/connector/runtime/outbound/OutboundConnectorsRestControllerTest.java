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

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.common.AbstractConnectorFactory.ConnectorRuntimeConfiguration;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.outbound.controller.OutboundConnectorResponse;
import io.camunda.connector.runtime.outbound.jobstream.GatewayConnectivityState;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = TestConnectorRuntimeApplication.class)
@AutoConfigureMockMvc
class OutboundConnectorsRestControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OutboundConnectorFactory connectorFactory;

  private static final String TYPE_1 = "io.camunda:http-json:1";
  private static final String TYPE_2 = "io.camunda:slack:1";
  private static final String TYPE_DISABLED = "io.camunda:disabled-connector:1";

  @BeforeEach
  void init() {
    when(connectorFactory.getRuntimeConfigurations())
        .thenReturn(
            List.of(
                new ConnectorRuntimeConfiguration<>(
                    new OutboundConnectorConfiguration(
                        "HTTP JSON", new String[] {"method", "url"}, TYPE_1, () -> null, 30000L),
                    true),
                new ConnectorRuntimeConfiguration<>(
                    new OutboundConnectorConfiguration(
                        "Slack", new String[] {"channel", "message"}, TYPE_2, () -> null, null),
                    true),
                new ConnectorRuntimeConfiguration<>(
                    new OutboundConnectorConfiguration(
                        "Disabled Connector", new String[] {}, TYPE_DISABLED, () -> null, null),
                    false)));
  }

  @Test
  void shouldReturnAllOutboundConnectors() throws Exception {
    var response =
        mockMvc
            .perform(get("/outbound"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<OutboundConnectorResponse> connectors =
        ConnectorsObjectMapperSupplier.getCopy().readValue(response, new TypeReference<>() {});

    assertEquals(3, connectors.size());

    var http = connectors.stream().filter(c -> c.type().equals(TYPE_1)).findFirst().orElseThrow();
    assertEquals("HTTP JSON", http.name());
    assertEquals(List.of("method", "url"), http.inputVariables());
    assertEquals(30000L, http.timeout());
    assertEquals("localhost", http.runtimeId());
    assertTrue(http.enabled());
    assertEquals(GatewayConnectivityState.UNKNOWN, http.gatewayConnectivityState());

    var slack = connectors.stream().filter(c -> c.type().equals(TYPE_2)).findFirst().orElseThrow();
    assertEquals("Slack", slack.name());
    assertEquals(List.of("channel", "message"), slack.inputVariables());
    assertEquals("localhost", slack.runtimeId());
    assertTrue(slack.enabled());
    assertEquals(GatewayConnectivityState.UNKNOWN, slack.gatewayConnectivityState());

    var disabled =
        connectors.stream().filter(c -> c.type().equals(TYPE_DISABLED)).findFirst().orElseThrow();
    assertEquals("Disabled Connector", disabled.name());
    assertFalse(disabled.enabled());
    assertEquals(GatewayConnectivityState.UNKNOWN, disabled.gatewayConnectivityState());
  }

  @Test
  void shouldReturnConnectorByType() throws Exception {
    var response =
        mockMvc
            .perform(get("/outbound/" + TYPE_1))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<OutboundConnectorResponse> connectors =
        ConnectorsObjectMapperSupplier.getCopy().readValue(response, new TypeReference<>() {});

    assertEquals(1, connectors.size());
    assertEquals(TYPE_1, connectors.getFirst().type());
    assertEquals("HTTP JSON", connectors.getFirst().name());
    assertTrue(connectors.getFirst().enabled());
  }

  @Test
  void shouldReturnDisabledConnector_whenQueriedByType() throws Exception {
    var response =
        mockMvc
            .perform(get("/outbound/" + TYPE_DISABLED))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<OutboundConnectorResponse> connectors =
        ConnectorsObjectMapperSupplier.getCopy().readValue(response, new TypeReference<>() {});

    assertEquals(1, connectors.size());
    assertFalse(connectors.getFirst().enabled());
  }

  @Test
  void shouldReturn404_whenUnknownType() throws Exception {
    mockMvc
        .perform(get("/outbound/unknown-type"))
        .andExpect(status().isNotFound())
        .andExpect(
            content()
                .string(
                    containsString(
                        "Data of type 'OutboundConnectorResponse' with id 'unknown-type' not found")));
  }
}
