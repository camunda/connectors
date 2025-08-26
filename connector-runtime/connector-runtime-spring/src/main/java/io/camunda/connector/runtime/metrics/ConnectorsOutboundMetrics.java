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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectorsOutboundMetrics {

  private final MeterRegistry meterRegistry;
  private final Map<String, Counter> invocationCounter = new ConcurrentHashMap<>();

  public ConnectorsOutboundMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void increaseInvocation(ActivatedJob job) {
    Result result = Result.getResult(job);
    this.invocationCounter
        .computeIfAbsent(
            result.createKey(ConnectorMetrics.Outbound.ACTION_ACTIVATED),
            s ->
                Counter.builder(ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS)
                    .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Outbound.ACTION_ACTIVATED)
                    .tag(ConnectorMetrics.Tag.TYPE, result.type())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_ID, result.id())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_VERSION, result.version())
                    .register(meterRegistry))
        .increment();
  }

  public void increaseFailure(ActivatedJob job) {
    Result result = Result.getResult(job);
    this.invocationCounter
        .computeIfAbsent(
            result.createKey(ConnectorMetrics.Outbound.ACTION_FAILED),
            s ->
                Counter.builder(ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS)
                    .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Outbound.ACTION_FAILED)
                    .tag(ConnectorMetrics.Tag.TYPE, result.type())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_ID, result.id())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_VERSION, result.version())
                    .register(meterRegistry))
        .increment();
  }

  public void increaseCompletion(ActivatedJob job) {
    Result result = Result.getResult(job);
    this.invocationCounter
        .computeIfAbsent(
            result.createKey(ConnectorMetrics.Outbound.ACTION_COMPLETED),
            s ->
                Counter.builder(ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS)
                    .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Outbound.ACTION_COMPLETED)
                    .tag(ConnectorMetrics.Tag.TYPE, result.type())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_ID, result.id())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_VERSION, result.version())
                    .register(meterRegistry))
        .increment();
  }

  public void increaseBpmnError(ActivatedJob job) {
    Result result = Result.getResult(job);
    this.invocationCounter
        .computeIfAbsent(
            result.createKey(ConnectorMetrics.Outbound.ACTION_BPMN_ERROR),
            s ->
                Counter.builder(ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS)
                    .tag(ConnectorMetrics.Tag.ACTION, ConnectorMetrics.Outbound.ACTION_BPMN_ERROR)
                    .tag(ConnectorMetrics.Tag.TYPE, result.type())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_ID, result.id())
                    .tag(ConnectorMetrics.Tag.ELEMENT_TEMPLATE_VERSION, result.version())
                    .register(meterRegistry))
        .increment();
  }

  public void executeWithTimer(ActivatedJob job, Runnable runnable) {
    Result result = Result.getResult(job);
    meterRegistry
        .timer(
            ConnectorMetrics.Outbound.METRIC_NAME_TIME,
            ConnectorMetrics.Tag.TYPE,
            result.type(),
            ConnectorMetrics.Tag.ELEMENT_TEMPLATE_ID,
            result.id(),
            ConnectorMetrics.Tag.ELEMENT_TEMPLATE_VERSION,
            result.version())
        .record(runnable);
  }
}
