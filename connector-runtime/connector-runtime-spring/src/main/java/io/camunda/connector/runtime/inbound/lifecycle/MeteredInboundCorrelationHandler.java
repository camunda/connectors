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

  public MeteredInboundCorrelationHandler(
      ZeebeClient zeebeClient, FeelEngineWrapper feelEngine, MetricsRecorder metricsRecorder) {
    super(zeebeClient, feelEngine);
    this.metricsRecorder = metricsRecorder;
  }

  @Override
  protected boolean isActivationConditionMet(
      InboundConnectorProperties properties, Object context) {
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
  public InboundConnectorResult<?> correlate(
      InboundConnectorProperties properties, Object variables) {
    metricsRecorder.increase(
        Inbound.METRIC_NAME_TRIGGERS, Inbound.ACTION_TRIGGERED, properties.getType());

    try {
      var result = super.correlate(properties, variables);
      if (result.isActivated()) {
        metricsRecorder.increase(
            Inbound.METRIC_NAME_TRIGGERS, Inbound.ACTION_CORRELATED, properties.getType());
      }
      return result;
    } catch (Exception e) {
      metricsRecorder.increase(
          Inbound.METRIC_NAME_TRIGGERS, Inbound.ACTION_CORRELATION_FAILED, properties.getType());
      throw e;
    }
  }
}
