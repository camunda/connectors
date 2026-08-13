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

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.metrics.MetricsRecorder.CounterMetricsContext;
import io.camunda.client.metrics.MetricsRecorder.TimerMetricsContext;
import java.util.Map;

public class ConnectorMetrics {

  public static class Tag {

    public static final String ELEMENT_TEMPLATE_ID = "elementTemplateId";
    public static final String TYPE = "type";
    public static final String ACTION = "action";
    public static final String ELEMENT_TEMPLATE_VERSION = "elementTemplateVersion";
    public static final String RESULT = "result";
    public static final String PHYSICAL_TENANT_ID = "physicalTenantId";
  }

  public static class Outbound {

    public static final String METRIC_NAME_INVOCATIONS = "camunda.connector.outbound.invocations";
    public static final String METRIC_NAME_TIME = "camunda.connector.outbound.execution-time";

    /** Jobs pulled from the Zeebe broker queue, tagged by connector {@code type}. */
    public static final String METRIC_NAME_WORKER_JOB_ACTIVATED =
        "camunda.client.worker.job.activated";

    /** Jobs acknowledged back to the Zeebe broker, tagged by connector {@code type}. */
    public static final String METRIC_NAME_WORKER_JOB_HANDLED = "camunda.client.worker.job.handled";

    /**
     * Epoch-millisecond timestamp of the last successfully completed job, per connector type. Value
     * is {@code 0} if no job has completed yet.
     */
    public static final String METRIC_NAME_LAST_COMPLETED =
        "camunda.connector.outbound.last-completed";

    /**
     * Epoch-millisecond timestamp of the last failed job, per connector type. Value is {@code 0} if
     * no job has failed yet.
     */
    public static final String METRIC_NAME_LAST_FAILED = "camunda.connector.outbound.last-failed";

    /**
     * All-time maximum execution duration in milliseconds, per connector type. Unlike the Timer's
     * built-in max (which decays after ~2 minutes of inactivity), this gauge is never reset.
     */
    public static final String METRIC_NAME_MAX_EXECUTION_TIME =
        "camunda.connector.outbound.max-execution-time";

    /**
     * Number of times a job-stream was recreated due to inactivity, tagged by connector {@code
     * type}. Spikes indicate broker connectivity instability.
     */
    public static final String METRIC_NAME_WORKER_STREAM_INACTIVITY_RECREATED =
        "camunda.client.worker.stream.inactivity.recreated";

    /** Value of the {@code action} tag for successfully completed jobs. */
    public static final String ACTION_COMPLETED = "completed";

    /** Value of the {@code action} tag for jobs that ended with a connector error. */
    public static final String ACTION_FAILED = "failed";

    /** Value of the {@code action} tag for jobs that threw a BPMN error. */
    public static final String ACTION_BPMN_ERROR = "bpmn-error";
  }

  public static class Inbound {
    public static final String METRIC_NAME_ACTIVATIONS = "camunda.connector.inbound.activations";
    public static final String METRIC_NAME_TRIGGERS = "camunda.connector.inbound.triggers";
    public static final String METRIC_NAME_INBOUND_PROCESS_DEFINITIONS_CHECKED =
        "camunda.connector.inbound.process-definitions-checked";

    /**
     * Number of process-definition inspection cache lookups, tagged by {@code result} ({@code hit}
     * or {@code miss}). A high miss rate suggests the cache size (configured via {@code
     * camunda.connector.inbound.process-definition-cache.max-size}) is too small.
     */
    public static final String METRIC_NAME_PROCESS_DEFINITION_CACHE_ACCESSES =
        "camunda.connector.inbound.process-definition-cache.accesses";

    /** Value of the {@code result} tag for a process-definition cache hit. */
    public static final String RESULT_CACHE_HIT = "hit";

    /** Value of the {@code result} tag for a process-definition cache miss. */
    public static final String RESULT_CACHE_MISS = "miss";

    /**
     * Number of process state changes that could not be published to the executable registry,
     * typically because the Orchestration Cluster was unreachable while the BPMN model was fetched.
     * Each one is retried on a subsequent poll, so a non-zero rate that does not settle indicates
     * connectors are failing to activate.
     */
    public static final String METRIC_NAME_PROCESS_STATE_CHANGE_PUBLISH_FAILURES =
        "camunda.connector.inbound.process-state-change.publish-failures";

    /**
     * Epoch-millisecond timestamp of the last successful activation, per connector type. Value is
     * {@code 0} if no activation has occurred yet.
     */
    public static final String METRIC_NAME_LAST_ACTIVATED =
        "camunda.connector.inbound.last-activated";

    /**
     * Epoch-millisecond timestamp of the last trigger attempt, per connector type. Value is {@code
     * 0} if no trigger has occurred yet.
     */
    public static final String METRIC_NAME_LAST_TRIGGERED =
        "camunda.connector.inbound.last-triggered";

    public static final String ACTION_ACTIVATED = "activated";
    public static final String ACTION_DEACTIVATED = "deactivated";
    public static final String ACTION_ACTIVATION_FAILED = "activation-failed";

    public static final String ACTION_TRIGGERED = "triggered";
    public static final String ACTION_ACTIVATION_CONDITION_FAILED = "activation-condition-failed";
    public static final String ACTION_CORRELATED = "correlated";
    public static final String ACTION_CORRELATION_FAILED = "correlation-failed";
  }

  /**
   * Physical tenant (engine) a meter is attributed to when none could be resolved — a job handled
   * by legacy single-client wiring that configures no {@code physical-tenant-id}. Mirrors the same
   * fallback applied to the scalar single-{@code CamundaClient} beans elsewhere in the runtime, so
   * both sides agree on the tenant a single-engine deployment reports.
   */
  public static final String DEFAULT_PHYSICAL_TENANT_ID = "default";

  /**
   * Attributes the counter to the job's own physical tenant. Callers that know which physical
   * tenant's job worker received the job should prefer {@link #counter(ActivatedJob, String)}, so
   * that the meter and the {@code /outbound} entry it belongs to always carry the same value.
   */
  public static CounterMetricsContext counter(ActivatedJob job) {
    return counter(job, null);
  }

  public static CounterMetricsContext counter(ActivatedJob job, String physicalTenantId) {
    Result result = Result.getResult(job);
    return new CounterMetricsContext(
        Outbound.METRIC_NAME_INVOCATIONS,
        Map.ofEntries(
            Map.entry(ConnectorMetrics.Tag.TYPE, result.type()),
            Map.entry(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_ID, result.id()),
            Map.entry(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_VERSION, result.version()),
            Map.entry(
                ConnectorMetrics.Tag.PHYSICAL_TENANT_ID,
                resolvePhysicalTenantId(physicalTenantId, result))),
        1);
  }

  /** See {@link #counter(ActivatedJob)}; the equivalent for the execution-time timer. */
  public static TimerMetricsContext timer(ActivatedJob job) {
    return timer(job, null);
  }

  public static TimerMetricsContext timer(ActivatedJob job, String physicalTenantId) {
    Result result = Result.getResult(job);
    return new TimerMetricsContext(
        ConnectorMetrics.Outbound.METRIC_NAME_TIME,
        Map.ofEntries(
            Map.entry(ConnectorMetrics.Tag.TYPE, result.type()),
            Map.entry(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_ID, result.id()),
            Map.entry(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_VERSION, result.version()),
            Map.entry(
                ConnectorMetrics.Tag.PHYSICAL_TENANT_ID,
                resolvePhysicalTenantId(physicalTenantId, result))));
  }

  /**
   * Prefers the physical tenant the job worker was opened for (the same ID the {@code /outbound}
   * listing and the per-physical-tenant document/secret beans are keyed by) over the one reported
   * on the job itself, so that a meter and the connector entry it belongs to always carry the same
   * value. Falls back to the job's own physical tenant, then to {@link
   * #DEFAULT_PHYSICAL_TENANT_ID}: a tag value is never allowed to be null.
   */
  private static String resolvePhysicalTenantId(String physicalTenantId, Result result) {
    if (physicalTenantId != null && !physicalTenantId.isBlank()) {
      return physicalTenantId;
    }
    if (result.physicalTenantId() != null && !result.physicalTenantId().isBlank()) {
      return result.physicalTenantId();
    }
    return DEFAULT_PHYSICAL_TENANT_ID;
  }
}
