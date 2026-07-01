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

import io.camunda.connector.runtime.inbound.controller.exception.InvalidTagFormatException;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.Collection;
import java.util.LinkedHashMap;
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
   * @throws InvalidTagFormatException if a tag string does not contain {@code :}
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
                throw new InvalidTagFormatException("Invalid tag (expected key:value): " + t);
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
    List<MetricResponse.Series> series =
        matched.stream()
            .map(
                m -> {
                  Map<String, String> tags =
                      m.getId().getTags().stream()
                          .collect(
                              Collectors.toMap(
                                  Tag::getKey, Tag::getValue, (a, b) -> a, LinkedHashMap::new));
                  Map<String, Double> measurements =
                      StreamSupport.stream(m.measure().spliterator(), false)
                          .collect(
                              Collectors.toMap(
                                  s -> s.getStatistic().name(),
                                  s -> s.getValue(),
                                  (a, b) -> a,
                                  LinkedHashMap::new));
                  return new MetricResponse.Series(tags, measurements);
                })
            .toList();

    return new MetricResponse(name, series);
  }
}
