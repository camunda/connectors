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
package io.camunda.connector.runtime;

import io.camunda.client.jobhandling.CamundaClientExecutorService;
import io.camunda.client.metrics.MeteredCamundaClientExecutorService;
import io.camunda.client.spring.configuration.CamundaAutoConfiguration;
import io.camunda.client.spring.configuration.ExecutorServiceConfiguration;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.connector.runtime.instances.InstanceForwardingConfiguration;
import io.camunda.connector.runtime.outbound.OutboundConnectorRuntimeConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@AutoConfigureBefore({CamundaAutoConfiguration.class, ExecutorServiceConfiguration.class})
@Import({OutboundConnectorRuntimeConfiguration.class, InstanceForwardingConfiguration.class})
public class OutboundConnectorsAutoConfiguration {

  // Camunda client default for camunda.client.worker.defaults.max-jobs-active
  private static final int DEFAULT_MAX_JOBS_ACTIVE = 32;

  @Bean(name = {"connectorCamundaClientExecutorService", "camundaClientExecutorService"})
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      name = "camunda.connector.virtual-threads.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public CamundaClientExecutorService connectorCamundaClientExecutorService(
      @Autowired(required = false) MeterRegistry meterRegistry) {
    ThreadFactory factory = Thread.ofVirtual().name("job-worker-virtual-", 0).factory();
    var vThreadExecutor = Executors.newThreadPerTaskExecutor(factory);
    var scheduler = Executors.newSingleThreadScheduledExecutor();
    return new MeteredCamundaClientExecutorService(
        scheduler, true, vThreadExecutor, true, meterRegistry);
  }

  /**
   * Platform-thread executor used when virtual threads are disabled.
   *
   * <p>Without it the SDK's executor would apply, sized by {@code camunda.client.execution-threads}
   * (default 1), which runs one job at a time. The pool is sized to at least the per-worker
   * in-flight cap ({@code camunda.client.worker.defaults.max-jobs-active}), so turning virtual
   * threads off keeps the same concurrency.
   */
  @Bean(name = {"connectorCamundaClientExecutorService", "camundaClientExecutorService"})
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "camunda.connector.virtual-threads.enabled", havingValue = "false")
  public CamundaClientExecutorService connectorPlatformThreadsExecutorService(
      CamundaClientProperties camundaClientProperties,
      @Autowired(required = false) MeterRegistry meterRegistry) {
    int threads = platformThreadPoolSize(camundaClientProperties);
    ThreadFactory factory = Thread.ofPlatform().name("job-worker-", 0).factory();
    var platformExecutor = Executors.newFixedThreadPool(threads, factory);
    var scheduler = Executors.newSingleThreadScheduledExecutor();
    return new MeteredCamundaClientExecutorService(
        scheduler, true, platformExecutor, true, meterRegistry);
  }

  static int platformThreadPoolSize(CamundaClientProperties properties) {
    int executionThreads =
        properties.getExecutionThreads() != null ? properties.getExecutionThreads() : 1;
    var workerDefaults =
        properties.getWorker() != null ? properties.getWorker().getDefaults() : null;
    int maxJobsActive =
        workerDefaults != null && workerDefaults.getMaxJobsActive() != null
            ? workerDefaults.getMaxJobsActive()
            : DEFAULT_MAX_JOBS_ACTIVE;
    return Math.max(executionThreads, maxJobsActive);
  }
}
