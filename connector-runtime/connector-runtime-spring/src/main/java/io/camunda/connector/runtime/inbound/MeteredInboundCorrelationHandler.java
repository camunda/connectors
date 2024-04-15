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
package io.camunda.connector.runtime.inbound;

import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.ProcessElementContextFactory;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.metrics.ConnectorMetrics.Inbound;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
import java.util.List;

public class MeteredInboundCorrelationHandler extends InboundCorrelationHandler {

  private final MetricsRecorder metricsRecorder;

  public MeteredInboundCorrelationHandler(
      ZeebeClient zeebeClient,
      FeelEngineWrapper feelEngine,
      MetricsRecorder metricsRecorder,
      ProcessElementContextFactory contextFactory) {
    super(zeebeClient, feelEngine, contextFactory);
    this.metricsRecorder = metricsRecorder;
  }

  @Override
  protected boolean isActivationConditionMet(InboundConnectorElement def, Object context) {
    boolean isConditionMet = super.isActivationConditionMet(def, context);
    if (!isConditionMet) {
      metricsRecorder.increase(
          Inbound.METRIC_NAME_TRIGGERS, Inbound.ACTION_ACTIVATION_CONDITION_FAILED, def.type());
    }
    return isConditionMet;
  }

  @Override
  public CorrelationResult correlate(List<InboundConnectorElement> elementList, Object variables) {
    if (elementList.isEmpty()) {
      throw new IllegalArgumentException("No elements to correlate, potential API misuse");
    }
    var type = elementList.getFirst().type();
    metricsRecorder.increase(Inbound.METRIC_NAME_TRIGGERS, Inbound.ACTION_TRIGGERED, type);

    try {
      var result = super.correlate(elementList, variables);
      metricsRecorder.increase(Inbound.METRIC_NAME_TRIGGERS, Inbound.ACTION_CORRELATED, type);
      return result;
    } catch (Exception e) {
      metricsRecorder.increase(
          Inbound.METRIC_NAME_TRIGGERS, Inbound.ACTION_CORRELATION_FAILED, type);
      throw e;
    }
  }
}
