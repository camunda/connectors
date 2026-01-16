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

  public ConnectorsInboundMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void increaseActivation(InboundConnectorElement connectorElement) {
    Result result = Result.getResult(connectorElement);
    this.activationCounter
        .computeIfAbsent(
            result.createKey(ConnectorMetrics.Inbound.ACTION_ACTIVATED),
            s ->
                Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS)
                    .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Inbound.ACTION_ACTIVATED)
                    .tag(ConnectorMetrics.Tag.TYPE, result.type())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_ID, result.id())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_VERSION, result.version())
                    .register(meterRegistry))
        .increment();
  }

  public void increaseDeactivation(InboundConnectorElement connectorElement) {
    Result result = Result.getResult(connectorElement);
    this.activationCounter
        .computeIfAbsent(
            result.createKey(ConnectorMetrics.Inbound.ACTION_DEACTIVATED),
            s ->
                Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS)
                    .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Inbound.ACTION_DEACTIVATED)
                    .tag(ConnectorMetrics.Tag.TYPE, result.type())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_ID, result.id())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_VERSION, result.version())
                    .register(meterRegistry))
        .increment();
  }

  public void increaseActivationFailure(InboundConnectorElement connectorElement) {
    Result result = Result.getResult(connectorElement);
    this.activationCounter
        .computeIfAbsent(
            result.createKey(ConnectorMetrics.Inbound.ACTION_ACTIVATION_FAILED),
            s ->
                Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_ACTIVATIONS)
                    .tag(
                        ConnectorMetrics.Tag.ACTION,
                        ConnectorMetrics.Inbound.ACTION_ACTIVATION_FAILED)
                    .tag(ConnectorMetrics.Tag.TYPE, result.type())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_ID, result.id())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_VERSION, result.version())
                    .register(meterRegistry))
        .increment();
  }

  public void increaseCorrelationSuccess(InboundConnectorElement connectorElement) {
    Result result = Result.getResult(connectorElement);
    this.activationCounter
        .computeIfAbsent(
            result.createKey(ConnectorMetrics.Inbound.ACTION_CORRELATED),
            s ->
                Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS)
                    .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Inbound.ACTION_CORRELATED)
                    .tag(ConnectorMetrics.Tag.TYPE, result.type())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_ID, result.id())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_VERSION, result.version())
                    .register(meterRegistry))
        .increment();
  }

  public void increaseCorrelationFailure(InboundConnectorElement connectorElement) {
    Result result = Result.getResult(connectorElement);
    this.activationCounter
        .computeIfAbsent(
            result.createKey(ConnectorMetrics.Inbound.ACTION_CORRELATION_FAILED),
            s ->
                Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS)
                    .tag(
                        ConnectorMetrics.Tag.ACTION,
                        ConnectorMetrics.Inbound.ACTION_CORRELATION_FAILED)
                    .tag(ConnectorMetrics.Tag.TYPE, result.type())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_ID, result.id())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_VERSION, result.version())
                    .register(meterRegistry))
        .increment();
  }

  public void increaseTrigger(InboundConnectorElement connectorElement) {
    Result result = Result.getResult(connectorElement);
    this.activationCounter
        .computeIfAbsent(
            result.createKey(ConnectorMetrics.Inbound.ACTION_TRIGGERED),
            s ->
                Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS)
                    .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Inbound.ACTION_TRIGGERED)
                    .tag(ConnectorMetrics.Tag.TYPE, result.type())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_ID, result.id())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_VERSION, result.version())
                    .register(meterRegistry))
        .increment();
  }

  public void increaseActivationConditionFailure(InboundConnectorElement connectorElement) {
    Result result = Result.getResult(connectorElement);
    this.activationCounter
        .computeIfAbsent(
            result.createKey(ConnectorMetrics.Inbound.ACTION_ACTIVATION_CONDITION_FAILED),
            s ->
                Counter.builder(ConnectorMetrics.Inbound.METRIC_NAME_TRIGGERS)
                    .tag(
                        ConnectorMetrics.Tag.ACTION,
                        ConnectorMetrics.Inbound.ACTION_ACTIVATION_CONDITION_FAILED)
                    .tag(ConnectorMetrics.Tag.TYPE, result.type())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_ID, result.id())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_VERSION, result.version())
                    .register(meterRegistry))
        .increment();
  }
}
