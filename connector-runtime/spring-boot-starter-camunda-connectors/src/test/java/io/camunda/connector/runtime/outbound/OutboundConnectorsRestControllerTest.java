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
import io.camunda.connector.runtime.metrics.ConnectorMetrics;
import io.camunda.connector.runtime.metrics.OutboundConnectorMetrics;
import io.camunda.connector.runtime.outbound.controller.OutboundConnectorResponse;
import io.camunda.connector.runtime.outbound.jobstream.BrokerConnectivityState;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    properties = {
      "camunda.connector.hostname=localhost",
      "camunda.connector.broker.monitoring.enabled=false",
    })
@AutoConfigureMockMvc
class OutboundConnectorsRestControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private MeterRegistry meterRegistry;

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
    // No broker monitoring configured in this test — broker state is UNKNOWN
    assertEquals(BrokerConnectivityState.UNKNOWN, http.brokerConnectivityState());

    var slack = connectors.stream().filter(c -> c.type().equals(TYPE_2)).findFirst().orElseThrow();
    assertEquals("Slack", slack.name());
    assertEquals(List.of("channel", "message"), slack.inputVariables());
    assertEquals("localhost", slack.runtimeId());
    assertTrue(slack.enabled());
    assertEquals(BrokerConnectivityState.UNKNOWN, slack.brokerConnectivityState());

    var disabled =
        connectors.stream().filter(c -> c.type().equals(TYPE_DISABLED)).findFirst().orElseThrow();
    assertEquals("Disabled Connector", disabled.name());
    assertFalse(disabled.enabled());
    assertEquals(BrokerConnectivityState.UNKNOWN, disabled.brokerConnectivityState());
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

  @Test
  void shouldReturnInvocations_groupedByType() throws Exception {
    // Use a unique type per test to avoid shared-context counter accumulation
    String type = "outbound-test-invocations";
    Counter.builder(ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS)
        .tag(ConnectorMetrics.Tag.TYPE, type)
        .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Outbound.ACTION_COMPLETED)
        .register(meterRegistry)
        .increment(10.0);
    Counter.builder(ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS)
        .tag(ConnectorMetrics.Tag.TYPE, type)
        .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Outbound.ACTION_FAILED)
        .register(meterRegistry)
        .increment(2.0);
    Counter.builder(ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS)
        .tag(ConnectorMetrics.Tag.TYPE, type)
        .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Outbound.ACTION_BPMN_ERROR)
        .register(meterRegistry)
        .increment(1.0);

    var response =
        mockMvc
            .perform(get("/outbound/metrics/" + type))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    OutboundConnectorMetrics m =
        ConnectorsObjectMapperSupplier.getCopy()
            .readValue(response, OutboundConnectorMetrics.class);

    assertEquals(10L, m.job().completed());
    assertEquals(2L, m.job().failed());
    assertEquals(1L, m.job().bpmnError());
  }

  @Test
  void shouldReturnWorkerStats_groupedByType() throws Exception {
    String type = "outbound-test-worker";
    Counter.builder(ConnectorMetrics.Outbound.METRIC_NAME_WORKER_JOB_ACTIVATED)
        .tag(ConnectorMetrics.Tag.TYPE, type)
        .register(meterRegistry)
        .increment(5.0);
    Counter.builder(ConnectorMetrics.Outbound.METRIC_NAME_WORKER_JOB_HANDLED)
        .tag(ConnectorMetrics.Tag.TYPE, type)
        .register(meterRegistry)
        .increment(4.0);
    Counter.builder(ConnectorMetrics.Outbound.METRIC_NAME_WORKER_STREAM_INACTIVITY_RECREATED)
        .tag(ConnectorMetrics.Tag.TYPE, type)
        .register(meterRegistry)
        .increment(1.0);

    var response =
        mockMvc
            .perform(get("/outbound/metrics/" + type))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    OutboundConnectorMetrics worker =
        ConnectorsObjectMapperSupplier.getCopy()
            .readValue(response, OutboundConnectorMetrics.class);

    assertEquals(5L, worker.worker().jobsActivated());
    assertEquals(4L, worker.worker().jobsHandled());
    assertEquals(1L, worker.worker().streamRecreations());
  }

  @Test
  void shouldFilterByConnectorType() throws Exception {
    String typeA = "outbound-test-filter-a";
    String typeB = "outbound-test-filter-b";
    Counter.builder(ConnectorMetrics.Outbound.METRIC_NAME_WORKER_JOB_ACTIVATED)
        .tag(ConnectorMetrics.Tag.TYPE, typeA)
        .register(meterRegistry)
        .increment(3.0);
    Counter.builder(ConnectorMetrics.Outbound.METRIC_NAME_WORKER_JOB_ACTIVATED)
        .tag(ConnectorMetrics.Tag.TYPE, typeB)
        .register(meterRegistry)
        .increment(7.0);

    var response =
        mockMvc
            .perform(get("/outbound/metrics/" + typeA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    OutboundConnectorMetrics m =
        ConnectorsObjectMapperSupplier.getCopy()
            .readValue(response, OutboundConnectorMetrics.class);

    assertEquals(3L, m.worker().jobsActivated());
  }

  @Test
  void shouldReturnMetricsByTypePath() throws Exception {
    String type = "outbound-test-path-type";
    Counter.builder(ConnectorMetrics.Outbound.METRIC_NAME_WORKER_JOB_ACTIVATED)
        .tag(ConnectorMetrics.Tag.TYPE, type)
        .register(meterRegistry)
        .increment(6.0);

    var response =
        mockMvc
            .perform(get("/outbound/metrics/" + type))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    OutboundConnectorMetrics m =
        ConnectorsObjectMapperSupplier.getCopy()
            .readValue(response, OutboundConnectorMetrics.class);

    assertEquals(6L, m.worker().jobsActivated());
  }

  @Test
  void shouldAggregateAcrossAllTypes_whenNoConnectorTypeProvided() throws Exception {
    String typeA = "outbound-test-agg-a";
    String typeB = "outbound-test-agg-b";
    Counter.builder(ConnectorMetrics.Outbound.METRIC_NAME_WORKER_JOB_ACTIVATED)
        .tag(ConnectorMetrics.Tag.TYPE, typeA)
        .register(meterRegistry)
        .increment(3.0);
    Counter.builder(ConnectorMetrics.Outbound.METRIC_NAME_WORKER_JOB_ACTIVATED)
        .tag(ConnectorMetrics.Tag.TYPE, typeB)
        .register(meterRegistry)
        .increment(7.0);

    var response =
        mockMvc
            .perform(get("/outbound/metrics"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    OutboundConnectorMetrics m =
        ConnectorsObjectMapperSupplier.getCopy()
            .readValue(response, OutboundConnectorMetrics.class);

    // jobsActivated must include at least the 10 (3+7) we just registered
    assertTrue(m.worker().jobsActivated() >= 10L);
  }
}
