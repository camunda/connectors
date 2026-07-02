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
 * Aggregated outbound connector metrics for a single connector type.
 *
 * @param connectorType job type (e.g. {@code io.camunda:http-json:1})
 * @param elementTemplateId element template ID associated with this connector type, or {@code null}
 *     if not available
 * @param elementTemplateVersion element template version, or {@code null} if not available
 * @param invocations counts per invocation outcome action
 * @param executionTime execution-time statistics derived from a Micrometer Timer
 * @param worker Zeebe job-worker level counters
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutboundConnectorMetrics(
    String connectorType,
    String elementTemplateId,
    String elementTemplateVersion,
    Invocations invocations,
    ExecutionTime executionTime,
    WorkerStats worker) {

  /**
   * @param completed jobs that completed successfully
   * @param failed jobs that ended with a connector error or exception
   * @param bpmnError jobs that threw a BPMN error
   */
  public record Invocations(long completed, long failed, long bpmnError) {}

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
  public record WorkerStats(long jobsActivated, long jobsHandled, long streamRecreations) {}
}
