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
import io.camunda.connector.runtime.instances.InstanceForwardingConfiguration;
import io.camunda.connector.runtime.outbound.OutboundConnectorRuntimeConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

@AutoConfiguration
@AutoConfigureBefore({CamundaAutoConfiguration.class, ExecutorServiceConfiguration.class})
@Import({OutboundConnectorRuntimeConfiguration.class, InstanceForwardingConfiguration.class})
public class OutboundConnectorsAutoConfiguration {

  static final String VIRTUAL_THREADS_PROPERTY = "camunda.connector.virtual-threads.enabled";

  /**
   * First Java version with JEP 491 ("Synchronize virtual threads without pinning"). From this
   * version on, a virtual thread can be unmounted while it holds a monitor, but it is still pinned
   * to its carrier while it waits for a monitor inside class loading or class initialization. When
   * all carriers wait for a monitor owned by an unmounted virtual thread, the JVM makes no more
   * progress. See https://github.com/camunda/connectors/issues/8440.
   */
  static final int FIRST_JAVA_VERSION_WITH_VIRTUAL_THREAD_CARRIER_STARVATION = 24;

  @Bean(name = {"connectorCamundaClientExecutorService", "camundaClientExecutorService"})
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      name = VIRTUAL_THREADS_PROPERTY,
      havingValue = "true",
      matchIfMissing = true)
  public CamundaClientExecutorService connectorCamundaClientExecutorService(
      @Autowired(required = false) MeterRegistry meterRegistry, Environment environment) {
    final ExecutorService jobExecutor =
        useVirtualThreads(environment, Runtime.version().feature())
            ? Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("job-worker-virtual-", 0).factory())
            : Executors.newThreadPerTaskExecutor(platformThreadFactory());
    var scheduler = Executors.newSingleThreadScheduledExecutor();
    return new MeteredCamundaClientExecutorService(
        scheduler, true, jobExecutor, true, meterRegistry);
  }

  /**
   * Virtual threads are the default for job handling, except on Java versions that are affected by
   * the carrier starvation described at {@link
   * #FIRST_JAVA_VERSION_WITH_VIRTUAL_THREAD_CARRIER_STARVATION}. There, platform threads are used
   * unless virtual threads are explicitly enabled with {@value #VIRTUAL_THREADS_PROPERTY}.
   */
  static boolean useVirtualThreads(Environment environment, int javaFeatureVersion) {
    return environment.containsProperty(VIRTUAL_THREADS_PROPERTY)
        || javaFeatureVersion < FIRST_JAVA_VERSION_WITH_VIRTUAL_THREAD_CARRIER_STARVATION;
  }

  private static ThreadFactory platformThreadFactory() {
    return Thread.ofPlatform().name("job-worker-", 0).daemon(true).factory();
  }
}
