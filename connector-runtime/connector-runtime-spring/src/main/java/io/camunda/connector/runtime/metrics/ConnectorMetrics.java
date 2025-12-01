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
  }

  public static class Outbound {

    public static final String METRIC_NAME_INVOCATIONS = "camunda.connector.outbound.invocations";
    public static final String METRIC_NAME_TIME = "camunda.connector.outbound.execution-time";
  }

  public static class Inbound {
    public static final String METRIC_NAME_ACTIVATIONS = "camunda.connector.inbound.activations";
    public static final String METRIC_NAME_TRIGGERS = "camunda.connector.inbound.triggers";
    public static final String METRIC_NAME_INBOUND_PROCESS_DEFINITIONS_CHECKED =
        "camunda.connector.inbound.process-definitions-checked";

    public static final String ACTION_ACTIVATED = "activated";
    public static final String ACTION_DEACTIVATED = "deactivated";
    public static final String ACTION_ACTIVATION_FAILED = "activation-failed";

    public static final String ACTION_TRIGGERED = "triggered";
    public static final String ACTION_ACTIVATION_CONDITION_FAILED = "activation-condition-failed";
    public static final String ACTION_CORRELATED = "correlated";
    public static final String ACTION_CORRELATION_FAILED = "correlation-failed";
  }

  public static CounterMetricsContext counter(ActivatedJob job) {
    Result result = Result.getResult(job);
    return new CounterMetricsContext(
        Outbound.METRIC_NAME_INVOCATIONS,
        Map.ofEntries(
            Map.entry(ConnectorMetrics.Tag.TYPE, result.type()),
            Map.entry(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_ID, result.id()),
            Map.entry(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_VERSION, result.version())),
        1);
  }

  public static TimerMetricsContext timer(ActivatedJob job) {
    Result result = Result.getResult(job);
    return new TimerMetricsContext(
        ConnectorMetrics.Outbound.METRIC_NAME_TIME,
        Map.ofEntries(
            Map.entry(ConnectorMetrics.Tag.TYPE, result.type()),
            Map.entry(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_ID, result.id()),
            Map.entry(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_VERSION, result.version())));
  }
}
