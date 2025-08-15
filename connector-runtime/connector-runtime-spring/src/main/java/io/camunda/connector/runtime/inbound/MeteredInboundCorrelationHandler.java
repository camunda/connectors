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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.api.inbound.CorrelationRequest;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.metrics.ConnectorsInboundMetrics;
import java.time.Duration;
import java.util.List;

public class MeteredInboundCorrelationHandler extends InboundCorrelationHandler {

  private final ConnectorsInboundMetrics connectorsInboundMetrics;

  public MeteredInboundCorrelationHandler(
      CamundaClient camundaClient,
      FeelEngineWrapper feelEngine,
      ObjectMapper objectMapper,
      Duration messageTtl,
      ConnectorsInboundMetrics connectorsInboundMetrics) {
    super(camundaClient, feelEngine, objectMapper, messageTtl);
    this.connectorsInboundMetrics = connectorsInboundMetrics;
  }

  @Override
  public boolean isActivationConditionMet(InboundConnectorElement def, Object context) {
    boolean isConditionMet = super.isActivationConditionMet(def, context);
    if (!isConditionMet) {
      this.connectorsInboundMetrics.increaseActivationConditionFailure(def);
    }
    return isConditionMet;
  }

  @Override
  public CorrelationResult correlate(
      List<InboundConnectorElement> elementList, CorrelationRequest correlationRequest) {
    if (elementList.isEmpty()) {
      throw new IllegalArgumentException("No elements to correlate, potential API misuse");
    }
    this.connectorsInboundMetrics.increaseTrigger(elementList.getFirst());
    try {
      var result = super.correlate(elementList, correlationRequest);
      this.connectorsInboundMetrics.increaseCorrelationSuccess(elementList.getFirst());
      return result;
    } catch (Exception e) {
      this.connectorsInboundMetrics.increaseCorrelationFailure(elementList.getFirst());
      throw e;
    }
  }
}
