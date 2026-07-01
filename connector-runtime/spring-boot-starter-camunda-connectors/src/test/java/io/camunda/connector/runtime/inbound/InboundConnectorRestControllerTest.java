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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import io.camunda.connector.runtime.metrics.ConnectorMetrics;
import io.camunda.connector.runtime.metrics.MetricResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
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
  void shouldReturnCuratedMetrics_whenNoNameProvided() throws Exception {
    Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS)
        .tag("type", "webhook")
        .register(meterRegistry)
        .increment(3.0);

    var response =
        mockMvc
            .perform(get("/inbound/metrics"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<MetricResponse> metrics =
        ConnectorsObjectMapperSupplier.getCopy().readValue(response, new TypeReference<>() {});

    var names = metrics.stream().map(MetricResponse::name).toList();
    assertTrue(names.contains(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS));
    // triggers not registered yet → skipped
    assertFalse(names.contains(ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS));
  }

  @Test
  void shouldReturnRequestedMetric_byName() throws Exception {
    Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS)
        .tag("type", "webhook")
        .register(meterRegistry)
        .increment(2.0);

    var response =
        mockMvc
            .perform(
                get("/inbound/metrics")
                    .param("name", ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<MetricResponse> metrics =
        ConnectorsObjectMapperSupplier.getCopy().readValue(response, new TypeReference<>() {});

    assertEquals(1, metrics.size());
    assertEquals(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS, metrics.getFirst().name());
    assertFalse(metrics.getFirst().meters().isEmpty());
  }

  @Test
  void shouldFilterMetrics_byTag() throws Exception {
    Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS)
        .tag("type", "webhook")
        .register(meterRegistry)
        .increment(6.0);
    Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS)
        .tag("type", "kafka")
        .register(meterRegistry)
        .increment(1.0);

    var response =
        mockMvc
            .perform(
                get("/inbound/metrics")
                    .param("name", ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS)
                    .param("tag", "type:webhook"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<MetricResponse> metrics =
        ConnectorsObjectMapperSupplier.getCopy().readValue(response, new TypeReference<>() {});

    assertEquals(1, metrics.size());
    var m = metrics.getFirst();
    assertEquals(1, m.meters().size());
    assertEquals(6.0, m.meters().getFirst().measurements().getFirst().value());
  }

  @Test
  void shouldReturnEmptyList_whenRequestedMetricNotRegistered() throws Exception {
    var response =
        mockMvc
            .perform(get("/inbound/metrics").param("name", "camunda.connector.inbound.unknown"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<MetricResponse> metrics =
        ConnectorsObjectMapperSupplier.getCopy().readValue(response, new TypeReference<>() {});

    assertTrue(metrics.isEmpty());
  }

  @Test
  void shouldReturn400_whenInvalidTagFormat() throws Exception {
    mockMvc
        .perform(get("/inbound/metrics").param("tag", "no-colon-here"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldQueryMultipleMetrics_byName() throws Exception {
    Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS)
        .tag("type", "webhook")
        .register(meterRegistry)
        .increment(2.0);
    Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS)
        .tag("type", "webhook")
        .register(meterRegistry)
        .increment(1.0);

    var response =
        mockMvc
            .perform(
                get("/inbound/metrics")
                    .param("name", ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS)
                    .param("name", ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<MetricResponse> metrics =
        ConnectorsObjectMapperSupplier.getCopy().readValue(response, new TypeReference<>() {});

    assertEquals(2, metrics.size());
    var names = metrics.stream().map(MetricResponse::name).toList();
    assertTrue(names.contains(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS));
    assertTrue(names.contains(ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS));
  }
}
