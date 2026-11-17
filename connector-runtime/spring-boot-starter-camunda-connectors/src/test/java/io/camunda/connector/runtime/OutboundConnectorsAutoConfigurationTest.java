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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.jobhandling.CamundaClientExecutorService;
import io.camunda.client.jobhandling.JobCallbackCommandWrapperFactory;
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.client.metrics.MeteredCamundaClientExecutorService;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.client.spring.configuration.CamundaAutoConfiguration;
import io.camunda.client.spring.configuration.ExecutorServiceConfiguration;
import io.camunda.client.spring.properties.CamundaClientJobWorkerProperties;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.client.spring.properties.CamundaClientWorkerProperties;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import io.camunda.connector.runtime.annotation.OutboundConnectorObjectMapper;
import io.camunda.connector.runtime.core.secret.SecretFilterFactory;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.outbound.job.ConfigurableSecretFilterFactory.SecretFilterMode;
import io.camunda.connector.runtime.outbound.lifecycle.OutboundConnectorManager;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

class OutboundConnectorsAutoConfigurationTest {

  private static final String CONNECTOR_EXECUTOR_BEAN_NAME =
      "connectorCamundaClientExecutorService";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ExecutorServiceConfiguration.class, OutboundConnectorsAutoConfiguration.class))
          .withUserConfiguration(RequiredOutboundRuntimeBeans.class);

  @Test
  void shouldCreateConnectorExecutorServiceByDefault() {
    contextRunner.run(context -> {
      assertThat(context).hasSingleBean(CamundaClientExecutorService.class);
      assertThat(context).hasBean(CONNECTOR_EXECUTOR_BEAN_NAME);
      assertThat(context.getBean(CamundaClientExecutorService.class))
          .isSameAs(context.getBean(CONNECTOR_EXECUTOR_BEAN_NAME))
          .isInstanceOf(MeteredCamundaClientExecutorService.class);
    });
  }

  @Test
  void shouldBeConfiguredBeforeCamundaClientAutoConfiguration() {
    var autoConfigureBefore =
        MergedAnnotations.from(OutboundConnectorsAutoConfiguration.class)
            .get(AutoConfigureBefore.class);

    assertThat(autoConfigureBefore.getClassArray("value"))
        .contains(CamundaAutoConfiguration.class, ExecutorServiceConfiguration.class);
  }

  @Test
  void shouldCreatePlatformThreadExecutorServiceWhenVirtualThreadsAreDisabled() {
    contextRunner
        .withPropertyValues("camunda.connector.virtual-threads.enabled=false")
        .run(context -> {
          assertThat(context).hasSingleBean(CamundaClientExecutorService.class);
          var executorService =
              context.getBean(CONNECTOR_EXECUTOR_BEAN_NAME, CamundaClientExecutorService.class);
          assertThat(context.getBean(CamundaClientExecutorService.class)).isSameAs(executorService);

          var jobExecutor = (ThreadPoolExecutor) executorService.getJobHandlingExecutor();
          assertThat(jobExecutor.getMaximumPoolSize()).isEqualTo(32);
          var threadIsVirtual = jobExecutor.submit(() -> Thread.currentThread().isVirtual());
          assertThat(threadIsVirtual.get()).isFalse();
        });
  }

  @Test
  void shouldSizePlatformThreadPoolToTheLargerOfExecutionThreadsAndMaxJobsActive() {
    var properties = new CamundaClientProperties();
    properties.setExecutionThreads(1);
    assertThat(OutboundConnectorsAutoConfiguration.platformThreadPoolSize(properties))
        .isEqualTo(32);

    var workerDefaults = new CamundaClientJobWorkerProperties();
    workerDefaults.setMaxJobsActive(100);
    var worker = new CamundaClientWorkerProperties();
    worker.setDefaults(workerDefaults);
    properties.setWorker(worker);
    assertThat(OutboundConnectorsAutoConfiguration.platformThreadPoolSize(properties))
        .isEqualTo(100);

    properties.setExecutionThreads(200);
    assertThat(OutboundConnectorsAutoConfiguration.platformThreadPoolSize(properties))
        .isEqualTo(200);
  }

  @Test
  void shouldDefaultSecretFilterModeToStrict() {
    contextRunner.run(context -> {
      var secretFilterFactory = context.getBean(SecretFilterFactory.class);
      assertThat(modeOf(secretFilterFactory)).isEqualTo(SecretFilterMode.STRICT);

      @SuppressWarnings("unchecked")
      var factoriesByPhysicalTenantId =
          (Map<String, SecretFilterFactory>)
              context.getBean(
                  "secretFilterFactoriesByPhysicalTenantId",
                  Map.class); // legacy-client fallback yields one entry, but assert on all
      assertThat(factoriesByPhysicalTenantId).isNotEmpty();
      factoriesByPhysicalTenantId
          .values()
          .forEach(factory -> assertThat(modeOf(factory)).isEqualTo(SecretFilterMode.STRICT));

      var manager = context.getBean(OutboundConnectorManager.class);
      @SuppressWarnings("unchecked")
      var managerFactories =
          (Map<String, SecretFilterFactory>)
              ReflectionTestUtils.getField(manager, "secretFilterFactoriesByPhysicalTenantId");
      assertThat(managerFactories).isNotEmpty();
      managerFactories
          .values()
          .forEach(factory -> assertThat(modeOf(factory)).isEqualTo(SecretFilterMode.STRICT));
    });
  }

  private static SecretFilterMode modeOf(SecretFilterFactory factory) {
    return (SecretFilterMode) ReflectionTestUtils.getField(factory, "secretFilterMode");
  }

  static class RequiredOutboundRuntimeBeans {

    @Bean
    CamundaClient camundaClient() {
      CamundaClient client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
      when(client.getConfiguration().getRestAddress())
          .thenReturn(URI.create("http://localhost:26500"));
      return client;
    }

    @Bean
    JobWorkerManager jobWorkerManager() {
      return mock(JobWorkerManager.class);
    }

    @Bean
    JobCallbackCommandWrapperFactory jobCallbackCommandWrapperFactory() {
      return mock(JobCallbackCommandWrapperFactory.class);
    }

    @Bean
    SecretProviderAggregator secretProviderAggregator() {
      return new SecretProviderAggregator(List.of());
    }

    @Bean
    MetricsRecorder metricsRecorder() {
      return mock(MetricsRecorder.class);
    }

    @Bean
    CamundaClientProperties camundaClientProperties() {
      var properties = new CamundaClientProperties();
      properties.setExecutionThreads(1);
      return properties;
    }

    @Bean
    @ConnectorsObjectMapper
    ObjectMapper connectorObjectMapper() {
      return ConnectorsObjectMapperSupplier.getCopy();
    }

    @Bean
    @OutboundConnectorObjectMapper
    ObjectMapper outboundConnectorObjectMapper() {
      return ConnectorsObjectMapperSupplier.getCopy();
    }
  }
}
