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
 * Aggregated outbound connector metrics, grouped into four sections.
 *
 * @param connector connector identity fields (type, element template)
 * @param runtime process-level runtime information
 * @param job job execution counters, timing, and last-activity timestamps
 * @param worker Zeebe job-worker level counters
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutboundConnectorMetrics(
    ConnectorInfo connector, Runtime runtime, Job job, Worker worker) {

  /**
   * @param connectorType job type (e.g. {@code io.camunda:http-json:1}), or {@code null} when
   *     representing aggregated totals across all types
   * @param elementTemplateId element template ID, or {@code null} if not available
   * @param elementTemplateVersion element template version, or {@code null} if not available
   */
  public record ConnectorInfo(
      String connectorType, String elementTemplateId, String elementTemplateVersion) {}

  /**
   * @param uptimeSeconds number of seconds the runtime process has been running
   */
  public record Runtime(Long uptimeSeconds) {}

  /**
   * @param completed jobs that completed successfully
   * @param failed jobs that ended with a connector error or exception
   * @param bpmnError jobs that threw a BPMN error
   * @param executionTime execution-time statistics, or {@code null} if no jobs have run yet
   * @param lastCompleted timestamp of the last successfully completed job, or {@code null} if none
   * @param lastFailed timestamp of the last failed job, or {@code null} if none
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Job(
      long completed,
      long failed,
      long bpmnError,
      ExecutionTime executionTime,
      Instant lastCompleted,
      Instant lastFailed) {}

  /**
   * @param meanMs mean execution duration in milliseconds
   * @param maxMs maximum execution duration in milliseconds
   */
  public record ExecutionTime(double meanMs, double maxMs) {}

  /**
   * @param jobsActivated jobs fetched from the Zeebe broker queue
   * @param jobsHandled jobs acknowledged back to the Zeebe broker
   * @param streamRecreations number of times the job-stream was recreated due to inactivity
   */
  public record Worker(long jobsActivated, long jobsHandled, long streamRecreations) {}
}
