package io.camunda.connector.runtime.metrics;

import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;

public class ConnectorMetrics {

  public static class Outbound {

    public static final String METRIC_NAME_INVOCATIONS = "camunda.connector.outbound.invocations";
    public static final String METRIC_NAME_TIME = "camunda.connector.outbound.execution-time";

    // same as job worker metrics from Spring Zeebe
    public static final String ACTION_ACTIVATED = MetricsRecorder.ACTION_ACTIVATED;
    public static final String ACTION_COMPLETED = MetricsRecorder.ACTION_COMPLETED;
    public static final String ACTION_FAILED = MetricsRecorder.ACTION_FAILED;
    public static final String ACTION_BPMN_ERROR = MetricsRecorder.ACTION_BPMN_ERROR;
  }

  public static class Inbound {
    public static final String METRIC_NAME_ACTIVATIONS = "camunda.connector.inbound.activations";
    public static final String METRIC_NAME_TRIGGERS = "camunda.connector.inbound.triggers";
    public static final String METRIC_NAME_INBOUND_PROCESS_DEFINITIONS_CHECKED = "camunda.connector.inbound.process-definitions-checked";

    public static final String ACTION_ACTIVATED = "activated";
    public static final String ACTION_DEACTIVATED = "deactivated";
    public static final String ACTION_ACTIVATION_FAILED = "activation-failed";

    public static final String ACTION_TRIGGERED = "triggered";
    public static final String ACTION_ACTIVATION_CONDITION_FAILED = "activation-condition-failed";
    public static final String ACTION_CORRELATED = "correlated";
    public static final String ACTION_CORRELATION_FAILED = "correlation-failed";
  }
}
