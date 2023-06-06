package io.camunda.connector.runtime.inbound.lifecycle;

import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.impl.inbound.InboundConnectorProperties;
import io.camunda.connector.runtime.core.feel.FeelEngineWrapper;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.metrics.ConnectorMetrics.Inbound;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;

public class MeteredInboundCorrelationHandler extends InboundCorrelationHandler {

  private final MetricsRecorder metricsRecorder;

  public MeteredInboundCorrelationHandler(ZeebeClient zeebeClient,
      FeelEngineWrapper feelEngine, MetricsRecorder metricsRecorder) {
    super(zeebeClient, feelEngine);
    this.metricsRecorder = metricsRecorder;
  }

  @Override
  protected boolean isActivationConditionMet(InboundConnectorProperties properties,
      Object context) {
    boolean isConditionMet = super.isActivationConditionMet(properties, context);
    if (!isConditionMet) {
      metricsRecorder.increase(
          Inbound.METRIC_NAME_TRIGGERS,
          Inbound.ACTION_ACTIVATION_CONDITION_FAILED,
          properties.getType());
    }
    return isConditionMet;
  }

  @Override
  public InboundConnectorResult<?> correlate(InboundConnectorProperties properties,
      Object variables) {
    metricsRecorder.increase(
        Inbound.METRIC_NAME_TRIGGERS,
        Inbound.ACTION_TRIGGERED,
        properties.getType());

    try {
      var result = super.correlate(properties, variables);
      if (result.isActivated()) {
        metricsRecorder.increase(
            Inbound.METRIC_NAME_TRIGGERS,
            Inbound.ACTION_CORRELATED,
            properties.getType());
      }
      return result;
    } catch (Exception e) {
      metricsRecorder.increase(
          Inbound.METRIC_NAME_TRIGGERS,
          Inbound.ACTION_CORRELATION_FAILED,
          properties.getType());
      throw e;
    }
  }
}
