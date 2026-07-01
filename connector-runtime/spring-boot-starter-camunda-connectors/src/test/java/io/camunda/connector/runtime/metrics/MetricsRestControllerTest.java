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
package io.camunda.connector.runtime.metrics;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    properties = {
      "camunda.connector.broker.monitoring.enabled=false",
    })
@AutoConfigureMockMvc
class MetricsRestControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private MeterRegistry meterRegistry;

  @Test
  void shouldReturnMetric_whenRegistered() throws Exception {
    Counter.builder("test.metric").tag("type", "http-json").register(meterRegistry).increment(3.0);

    var response =
        mockMvc
            .perform(get("/metrics/test.metric"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    MetricResponse metric =
        ConnectorsObjectMapperSupplier.getCopy().readValue(response, MetricResponse.class);

    assertEquals("test.metric", metric.name());
    assertEquals(1, metric.meters().size());
    var meter = metric.meters().getFirst();
    assertEquals(1, meter.measurements().size());
    assertEquals("COUNT", meter.measurements().getFirst().statistic());
    assertEquals(3.0, meter.measurements().getFirst().value());
    assertEquals(1, meter.tags().size());
    assertEquals("type", meter.tags().getFirst().tag());
    assertEquals("http-json", meter.tags().getFirst().value());
    assertEquals(1, metric.availableTags().size());
    assertEquals("type", metric.availableTags().getFirst().tag());
    assertEquals(List.of("http-json"), metric.availableTags().getFirst().values());
  }

  @Test
  void shouldFilterMetric_byTag() throws Exception {
    Counter.builder("test.filtered.metric")
        .tag("type", "http-json")
        .register(meterRegistry)
        .increment(5.0);
    Counter.builder("test.filtered.metric")
        .tag("type", "slack")
        .register(meterRegistry)
        .increment(2.0);

    var result =
        mockMvc
            .perform(get("/metrics/test.filtered.metric").param("tag", "type:http-json"))
            .andReturn();
    var response = result.getResponse().getContentAsString();

    MetricResponse metric =
        ConnectorsObjectMapperSupplier.getCopy().readValue(response, MetricResponse.class);

    assertEquals(1, metric.availableTags().size());
    assertEquals(List.of("http-json"), metric.availableTags().getFirst().values());
    assertEquals(1, metric.meters().size());
    assertEquals(5.0, metric.meters().getFirst().measurements().getFirst().value());
  }

  @Test
  void shouldReturn404_whenMetricNotFound() throws Exception {
    mockMvc
        .perform(get("/metrics/unknown.metric"))
        .andExpect(status().isNotFound())
        .andExpect(content().string(containsString("unknown.metric")));
  }

  @Test
  void shouldReturn400_whenInvalidTagFormat() throws Exception {
    mockMvc
        .perform(get("/metrics/any.metric").param("tag", "no-colon-here"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnAllMetricsWithValues() throws Exception {
    Counter.builder("test.list.metric.a").register(meterRegistry).increment();
    Counter.builder("test.list.metric.b").register(meterRegistry).increment();

    var response =
        mockMvc
            .perform(get("/metrics"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<MetricResponse> metrics =
        ConnectorsObjectMapperSupplier.getCopy().readValue(response, new TypeReference<>() {});

    var names = metrics.stream().map(MetricResponse::name).toList();
    assertTrue(names.contains("test.list.metric.a"));
    assertTrue(names.contains("test.list.metric.b"));
    // results are sorted by name
    assertEquals(names.stream().sorted().toList(), names);
    // each entry carries measurements
    metrics.forEach(m -> assertFalse(m.meters().isEmpty()));
  }

  @Test
  void shouldReturnSameStructureInListAndByName() throws Exception {
    Counter.builder("test.struct.metric")
        .tag("type", "http-json")
        .register(meterRegistry)
        .increment(7.0);

    var listResponse =
        mockMvc
            .perform(get("/metrics"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<MetricResponse> allMetrics =
        ConnectorsObjectMapperSupplier.getCopy().readValue(listResponse, new TypeReference<>() {});
    MetricResponse fromList =
        allMetrics.stream()
            .filter(m -> m.name().equals("test.struct.metric"))
            .findFirst()
            .orElseThrow();

    var singleResponse =
        mockMvc
            .perform(get("/metrics/test.struct.metric"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    MetricResponse fromSingle =
        ConnectorsObjectMapperSupplier.getCopy().readValue(singleResponse, MetricResponse.class);

    assertEquals(fromSingle, fromList);
  }
}
