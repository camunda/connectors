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

import io.camunda.connector.runtime.inbound.controller.exception.DataNotFoundException;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/metrics")
public class MetricsRestController {

  private final MeterRegistry meterRegistry;

  public MetricsRestController(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /**
   * Returns all registered metrics with their current values, sorted by name.
   *
   * @return all metrics with measurements
   */
  @GetMapping
  public List<MetricResponse> getAllMetrics() {
    return meterRegistry.getMeters().stream()
        .collect(Collectors.groupingBy(m -> m.getId().getName()))
        .entrySet()
        .stream()
        .sorted(Map.Entry.comparingByKey())
        .map(e -> buildMetricResponse(e.getKey(), e.getValue()))
        .toList();
  }

  /**
   * Returns measurements for the named metric, optionally filtered by tags.
   *
   * <p>Tags are provided as {@code key:value} pairs, e.g. {@code ?tag=type:io.camunda:http-json:1}.
   * Multiple {@code tag} parameters can be supplied to narrow the match.
   *
   * @param name metric name (e.g. {@code camunda.connector.outbound.invocations})
   * @param tags optional list of {@code key:value} tag filters
   * @throws DataNotFoundException when no meter with the given name (and tags) is registered
   */
  @GetMapping("/{name}")
  public MetricResponse getMetric(
      @PathVariable("name") String name,
      @RequestParam(name = "tag", required = false) List<String> tags) {

    List<Tag> filterTags =
        tags == null
            ? List.of()
            : tags.stream()
                .map(
                    t -> {
                      int colon = t.indexOf(':');
                      if (colon < 0)
                        throw new IllegalArgumentException(
                            "Invalid tag (expected key:value): " + t);
                      return Tag.of(t.substring(0, colon), t.substring(colon + 1));
                    })
                .toList();

    Collection<Meter> matched = meterRegistry.find(name).tags(filterTags).meters();

    if (matched.isEmpty()) {
      throw new DataNotFoundException(MetricResponse.class, name);
    }

    return buildMetricResponse(name, matched);
  }

  private MetricResponse buildMetricResponse(String name, Collection<Meter> matched) {
    List<MetricResponse.Meter> meters =
        matched.stream()
            .map(
                m ->
                    new MetricResponse.Meter(
                        m.getId().getTags().stream()
                            .map(t -> new MetricResponse.MetricTag(t.getKey(), t.getValue()))
                            .toList(),
                        StreamSupport.stream(m.measure().spliterator(), false)
                            .map(
                                s ->
                                    new MetricResponse.Measurement(
                                        s.getStatistic().name(), s.getValue()))
                            .toList()))
            .toList();

    Map<String, List<String>> tagValues =
        matched.stream()
            .flatMap(m -> m.getId().getTags().stream())
            .collect(
                Collectors.groupingBy(
                    Tag::getKey, Collectors.mapping(Tag::getValue, Collectors.toList())));

    List<MetricResponse.AvailableTag> availableTags =
        tagValues.entrySet().stream()
            .map(
                e ->
                    new MetricResponse.AvailableTag(
                        e.getKey(), e.getValue().stream().distinct().toList()))
            .toList();

    return new MetricResponse(name, meters, availableTags);
  }
}
