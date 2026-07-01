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

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** Shared helpers for querying metrics and building {@link MetricResponse} objects. */
public final class MetricsQueryHelper {

  private MetricsQueryHelper() {}

  /**
   * Queries the registry for the given metric names, applying optional tag filters. When {@code
   * names} is empty, {@code curatedNames} is used instead. Metrics with no registered meters are
   * silently skipped.
   */
  public static List<MetricResponse> queryMetrics(
      MeterRegistry meterRegistry,
      List<String> names,
      List<String> tagParams,
      List<String> curatedNames) {

    List<Tag> filterTags = parseTags(tagParams);
    List<String> effectiveNames = (names == null || names.isEmpty()) ? curatedNames : names;

    return effectiveNames.stream()
        .map(name -> buildMetricResponseOrNull(meterRegistry, name, filterTags))
        .filter(Objects::nonNull)
        .toList();
  }

  /**
   * Parses {@code key:value} tag strings into Micrometer {@link Tag} objects.
   *
   * @throws IllegalArgumentException if a tag string does not contain {@code :}
   */
  public static List<Tag> parseTags(List<String> tagParams) {
    if (tagParams == null) {
      return List.of();
    }
    return tagParams.stream()
        .map(
            t -> {
              int colon = t.indexOf(':');
              if (colon < 0) {
                throw new IllegalArgumentException("Invalid tag (expected key:value): " + t);
              }
              return Tag.of(t.substring(0, colon), t.substring(colon + 1));
            })
        .toList();
  }

  private static MetricResponse buildMetricResponseOrNull(
      MeterRegistry meterRegistry, String name, List<Tag> filterTags) {
    Collection<Meter> matched = meterRegistry.find(name).tags(filterTags).meters();
    if (matched.isEmpty()) {
      return null;
    }
    return buildMetricResponse(name, matched);
  }

  /** Builds a {@link MetricResponse} from a collection of meters sharing the same metric name. */
  public static MetricResponse buildMetricResponse(String name, Collection<Meter> matched) {
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
