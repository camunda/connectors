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

import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectorsInboundMetrics {

  private final MeterRegistry meterRegistry;
  private final Map<String, Counter> activationCounter = new ConcurrentHashMap<>();
  private final Map<String, Counter> deactivationCounter = new ConcurrentHashMap<>();
  private final Map<String, Counter> correlationSuccessCounter = new ConcurrentHashMap<>();
  private final Map<String, Counter> correlationFailureCounter = new ConcurrentHashMap<>();
  private final Map<String, Counter> activationConditionFailureCounter = new ConcurrentHashMap<>();
  private final Map<String, Counter> triggerCounter = new ConcurrentHashMap<>();
  private final Map<String, Counter> processDefinitionsChecked = new ConcurrentHashMap<>();

  public ConnectorsInboundMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void increaseActivation(InboundConnectorElement connectorElement) {
    Result result = Result.getResult(connectorElement);
    this.activationCounter
        .computeIfAbsent(
            result.key(),
            s ->
                Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS)
                    .tag("type", result.type())
                    .tag("id", result.id())
                    .tag("version", result.version())
                    .register(meterRegistry))
        .increment();
  }

  public void increaseDeactivation(InboundConnectorElement connectorElement) {
    Result result = Result.getResult(connectorElement);
    this.deactivationCounter
        .computeIfAbsent(
            result.key(),
            s ->
                Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_DEACTIVATIONS)
                    .tag("type", result.type())
                    .tag("id", result.id())
                    .tag("version", result.version())
                    .register(meterRegistry))
        .increment();
  }

  public void increaseCorrelationSuccess(InboundConnectorElement connectorElement) {
    Result result = Result.getResult(connectorElement);
    this.correlationSuccessCounter
        .computeIfAbsent(
            result.key(),
            s ->
                Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_CORRELATION_SUCCESS)
                    .tag("type", result.type())
                    .tag("id", result.id())
                    .tag("version", result.version())
                    .register(meterRegistry))
        .increment();
  }

  public void increaseCorrelationFailure(InboundConnectorElement connectorElement) {
    Result result = Result.getResult(connectorElement);
    this.correlationFailureCounter
        .computeIfAbsent(
            result.key(),
            s ->
                Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_CORRELATION_FAILURE)
                    .tag("type", result.type())
                    .tag("id", result.id())
                    .tag("version", result.version())
                    .register(meterRegistry))
        .increment();
  }

  public void increaseTrigger(InboundConnectorElement connectorElement) {
    Result result = Result.getResult(connectorElement);
    this.triggerCounter
        .computeIfAbsent(
            result.key(),
            s ->
                Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS)
                    .tag("type", result.type())
                    .tag("id", result.id())
                    .tag("version", result.version())
                    .register(meterRegistry))
        .increment();
  }

  public void increaseActivationConditionFailure(InboundConnectorElement connectorElement) {
    Result result = Result.getResult(connectorElement);
    this.activationConditionFailureCounter
        .computeIfAbsent(
            result.key(),
            s ->
                Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATION_CONDITION_FAILURE)
                    .tag("type", result.type())
                    .tag("id", result.id())
                    .tag("version", result.version())
                    .register(meterRegistry))
        .increment();
  }

  public void increaseProcessDefinitionsChecked(int count) {
    this.processDefinitionsChecked
        .computeIfAbsent(
            "PROCESS_DEFINITIONS_CHECKED",
            s ->
                Counter.builder(
                        ConnectorMetrics.Inbound.METRIC_NAME_INBOUND_PROCESS_DEFINITIONS_CHECKED)
                    .register(meterRegistry))
        .increment(count);
  }
}
