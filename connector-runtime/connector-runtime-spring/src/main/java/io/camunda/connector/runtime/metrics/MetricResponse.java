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

import java.util.List;

/**
 * Represents a metric measurement result.
 *
 * @param name the metric name
 * @param meters the matched meters (series), each with its own tags and measurements
 * @param availableTags tag key/values available on the matched meters
 */
public record MetricResponse(String name, List<Meter> meters, List<AvailableTag> availableTags) {

  /**
   * A single matched meter (series) identified by its tag combination.
   *
   * @param tags the tags identifying this series
   * @param measurements list of statistic/value pairs (e.g. COUNT, TOTAL, MAX)
   */
  public record Meter(List<MetricTag> tags, List<Measurement> measurements) {}

  public record MetricTag(String tag, String value) {}

  public record Measurement(String statistic, double value) {}

  public record AvailableTag(String tag, List<String> values) {}
}
