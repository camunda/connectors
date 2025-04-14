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

  public ConnectorsInboundMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void increaseActivation(String connectorType, String version) {
    String key = connectorType + "_" + version;
    this.activationCounter
        .computeIfAbsent(
            key,
            s ->
                Counter.builder("connectors_inbound_activation")
                    .tag("type", connectorType)
                    .tag("version", version)
                    .register(meterRegistry))
        .increment();
  }

  public void increaseDeactivation(String connectorType, String version) {
    String key = connectorType + "_" + version;
    this.deactivationCounter
        .computeIfAbsent(
            key,
            s ->
                Counter.builder("connectors_inbound_deactivation")
                    .tag("type", connectorType)
                    .tag("version", version)
                    .register(meterRegistry))
        .increment();
  }

  public void increaseCorrelationSuccess(String connectorType, String version) {
    String key = connectorType + "_" + version;
    this.correlationSuccessCounter
        .computeIfAbsent(
            key,
            s ->
                Counter.builder("connectors_inbound_correlation_success")
                    .tag("type", connectorType)
                    .tag("version", version)
                    .register(meterRegistry))
        .increment();
  }

  public void increaseCorrelationFailure(String connectorType, String version) {
    String key = connectorType + "_" + version;
    this.correlationFailureCounter
        .computeIfAbsent(
            key,
            s ->
                Counter.builder("connectors_inbound_correlation_failure")
                    .tag("type", connectorType)
                    .tag("version", version)
                    .register(meterRegistry))
        .increment();
  }

  public void increaseTrigger(String connectorType, String version) {
    String key = connectorType + "_" + version;
    this.triggerCounter
        .computeIfAbsent(
            key,
            s ->
                Counter.builder("connectors_inbound_triggered")
                    .tag("type", connectorType)
                    .tag("version", version)
                    .register(meterRegistry))
        .increment();
  }

  public void increaseActivationConditionFailure(String connectorType, String version) {
    String key = connectorType + "_" + version;
    this.activationConditionFailureCounter
        .computeIfAbsent(
            key,
            s ->
                Counter.builder("connectors_inbound_activation_condition_failure")
                    .tag("type", connectorType)
                    .tag("version", version)
                    .register(meterRegistry))
        .increment();
  }
}
