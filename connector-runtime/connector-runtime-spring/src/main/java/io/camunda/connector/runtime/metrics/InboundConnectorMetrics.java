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

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Aggregated inbound connector metrics for a single connector type.
 *
 * @param connectorType connector type (e.g. {@code io.camunda:webhook:1}), or {@code null} when
 *     representing aggregated totals across all types
 * @param activations activation lifecycle counters
 * @param triggers correlation / trigger counters
 * @param runtimeUptimeSeconds number of seconds the runtime process has been running
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InboundConnectorMetrics(
    String connectorType, Activations activations, Triggers triggers, Long runtimeUptimeSeconds) {

  /**
   * @param activated number of successful activations
   * @param deactivated number of deactivations
   * @param activationFailed number of failed activations
   */
  public record Activations(long activated, long deactivated, long activationFailed) {}

  /**
   * @param triggered number of trigger attempts (inbound event received)
   * @param correlated number of successfully correlated process instances
   * @param correlationFailed number of correlation failures
   * @param activationConditionFailed number of events filtered by activation condition
   */
  public record Triggers(
      long triggered, long correlated, long correlationFailed, long activationConditionFailed) {}
}
