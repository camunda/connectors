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
  private final Map<String, Counter> failureCounter = new ConcurrentHashMap<>();
  private final Map<String, Counter> completionCounter = new ConcurrentHashMap<>();
  private final Map<String, Counter> bpmnErrorCounter = new ConcurrentHashMap<>();

  public ConnectorsOutboundMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void increaseInvocation(ActivatedJob job) {
    Result result = Result.getResult(job);
    this.invocationCounter
        .computeIfAbsent(
            result.key(),
            s ->
                Counter.builder(ConnectorMetrics.Outbound.METRIC_NAME_INVOCATIONS)
                    .tag("type", result.type())
                    .tag("id", result.id())
                    .tag("version", result.version())
                    .register(meterRegistry))
        .increment();
  }

  public void increaseFailure(ActivatedJob job) {
    Result result = Result.getResult(job);
    this.failureCounter
        .computeIfAbsent(
            result.key(),
            s ->
                Counter.builder(ConnectorMetrics.Outbound.METRIC_NAME_FAILURES)
                    .tag("type", result.type())
                    .tag("id", result.id())
                    .tag("version", result.version())
                    .register(meterRegistry))
        .increment();
  }

  public void executeWithTimer(ActivatedJob job, Runnable runnable) {
    Result result = Result.getResult(job);
    meterRegistry
        .timer(
            ConnectorMetrics.Outbound.METRIC_NAME_TIME,
            "type",
            result.type(),
            "id",
            result.id(),
            "version",
            result.version())
        .record(runnable);
  }

  public void increaseCompletion(ActivatedJob job) {
    Result result = Result.getResult(job);
    this.completionCounter
        .computeIfAbsent(
            result.key(),
            s ->
                Counter.builder(ConnectorMetrics.Outbound.METRIC_NAME_COMPLETIONS)
                    .tag("type", result.type())
                    .tag("id", result.id())
                    .tag("version", result.version())
                    .register(meterRegistry))
        .increment();
  }

  public void increaseBpmnError(ActivatedJob job) {
    Result result = Result.getResult(job);
    this.bpmnErrorCounter
        .computeIfAbsent(
            result.key(),
            s ->
                Counter.builder(ConnectorMetrics.Outbound.METRIC_NAME_BPMN_ERRORS)
                    .tag("type", result.type())
                    .tag("id", result.id())
                    .tag("version", result.version())
                    .register(meterRegistry))
        .increment();
  }
}
