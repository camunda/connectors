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
import java.time.Instant;

/**
 * Aggregated inbound connector metrics, grouped into three sections.
 *
 * @param runtime process-level runtime information
 * @param activation activation lifecycle counters and last-activity timestamp
 * @param trigger correlation / trigger counters and last-activity timestamp
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InboundConnectorMetrics(Runtime runtime, Activation activation, Trigger trigger) {

  /**
   * @param uptimeSeconds number of seconds the runtime process has been running
   */
  public record Runtime(Long uptimeSeconds) {}

  /**
   * @param activated number of successful activations
   * @param deactivated number of deactivations
   * @param activationFailed number of failed activations
   * @param lastActivated timestamp of the last successful activation, or {@code null} if none yet
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Activation(
      long activated, long deactivated, long activationFailed, Instant lastActivated) {}

  /**
   * @param triggered number of trigger attempts (inbound event received)
   * @param correlated number of successfully correlated process instances
   * @param correlationFailed number of correlation failures
   * @param activationConditionFailed number of events filtered by activation condition
   * @param lastTriggered timestamp of the last trigger attempt, or {@code null} if none yet
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Trigger(
      long triggered,
      long correlated,
      long correlationFailed,
      long activationConditionFailed,
      Instant lastTriggered) {}
}
