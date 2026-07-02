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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import io.camunda.connector.runtime.metrics.ConnectorMetrics;
import io.camunda.connector.runtime.metrics.InboundConnectorMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = TestConnectorRuntimeApplication.class)
@AutoConfigureMockMvc
class InboundConnectorRestControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private MeterRegistry meterRegistry;

  // Required by InboundConnectorRestController wiring
  @MockitoBean private InboundExecutableRegistry executableRegistry;

  @Test
  void shouldReturnActivations_groupedByType() throws Exception {
    // Use a unique type per test to avoid shared-context counter accumulation
    String type = "inbound-test-activations";
    Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS)
        .tag(ConnectorMetrics.Tag.TYPE, type)
        .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Inbound.ACTION_ACTIVATED)
        .register(meterRegistry)
        .increment(3.0);
    Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS)
        .tag(ConnectorMetrics.Tag.TYPE, type)
        .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Inbound.ACTION_ACTIVATION_FAILED)
        .register(meterRegistry)
        .increment(1.0);

    var response =
        mockMvc
            .perform(get("/inbound/metrics/" + type))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    InboundConnectorMetrics m =
        ConnectorsObjectMapperSupplier.getCopy().readValue(response, InboundConnectorMetrics.class);

    assertEquals(3L, m.activations().activated());
    assertEquals(0L, m.activations().deactivated());
    assertEquals(1L, m.activations().activationFailed());
  }

  @Test
  void shouldReturnTriggers_groupedByType() throws Exception {
    String type = "inbound-test-triggers";
    Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS)
        .tag(ConnectorMetrics.Tag.TYPE, type)
        .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Inbound.ACTION_TRIGGERED)
        .register(meterRegistry)
        .increment(10.0);
    Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS)
        .tag(ConnectorMetrics.Tag.TYPE, type)
        .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Inbound.ACTION_CORRELATED)
        .register(meterRegistry)
        .increment(9.0);
    Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS)
        .tag(ConnectorMetrics.Tag.TYPE, type)
        .tag(
            ConnectorMetrics.Tag.ACTION,
            ConnectorMetrics.Inbound.ACTION_ACTIVATION_CONDITION_FAILED)
        .register(meterRegistry)
        .increment(1.0);

    var response =
        mockMvc
            .perform(get("/inbound/metrics/" + type))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    InboundConnectorMetrics m =
        ConnectorsObjectMapperSupplier.getCopy().readValue(response, InboundConnectorMetrics.class);

    assertEquals(10L, m.triggers().triggered());
    assertEquals(9L, m.triggers().correlated());
    assertEquals(0L, m.triggers().correlationFailed());
    assertEquals(1L, m.triggers().activationConditionFailed());
  }

  @Test
  void shouldFilterByConnectorType() throws Exception {
    String typeA = "inbound-test-filter-a";
    String typeB = "inbound-test-filter-b";
    Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS)
        .tag(ConnectorMetrics.Tag.TYPE, typeA)
        .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Inbound.ACTION_ACTIVATED)
        .register(meterRegistry)
        .increment(5.0);
    Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS)
        .tag(ConnectorMetrics.Tag.TYPE, typeB)
        .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Inbound.ACTION_ACTIVATED)
        .register(meterRegistry)
        .increment(2.0);

    var response =
        mockMvc
            .perform(get("/inbound/metrics/" + typeA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    InboundConnectorMetrics m =
        ConnectorsObjectMapperSupplier.getCopy().readValue(response, InboundConnectorMetrics.class);

    assertNull(m.connectorType());
    assertEquals(5L, m.activations().activated());
  }

  @Test
  void shouldReturnMetricsByTypePath() throws Exception {
    String type = "inbound-test-path-type";
    Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS)
        .tag(ConnectorMetrics.Tag.TYPE, type)
        .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Inbound.ACTION_ACTIVATED)
        .register(meterRegistry)
        .increment(4.0);

    var response =
        mockMvc
            .perform(get("/inbound/metrics/" + type))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    InboundConnectorMetrics m =
        ConnectorsObjectMapperSupplier.getCopy().readValue(response, InboundConnectorMetrics.class);

    assertNull(m.connectorType());
    assertEquals(4L, m.activations().activated());
  }

  @Test
  void shouldAggregateAcrossAllTypes_whenNoConnectorTypeProvided() throws Exception {
    String typeA = "inbound-test-agg-a";
    String typeB = "inbound-test-agg-b";
    Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS)
        .tag(ConnectorMetrics.Tag.TYPE, typeA)
        .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Inbound.ACTION_ACTIVATED)
        .register(meterRegistry)
        .increment(5.0);
    Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS)
        .tag(ConnectorMetrics.Tag.TYPE, typeB)
        .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Inbound.ACTION_ACTIVATED)
        .register(meterRegistry)
        .increment(3.0);

    var response =
        mockMvc
            .perform(get("/inbound/metrics"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    InboundConnectorMetrics m =
        ConnectorsObjectMapperSupplier.getCopy().readValue(response, InboundConnectorMetrics.class);

    // connectorType is null (omitted) for the aggregate response
    assertNull(m.connectorType());
    // activated must include at least the 8 (5+3) we just registered
    assertTrue(m.activations().activated() >= 8L);
  }
}
