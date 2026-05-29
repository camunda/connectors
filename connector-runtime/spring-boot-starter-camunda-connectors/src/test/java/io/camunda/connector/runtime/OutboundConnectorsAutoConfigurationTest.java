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
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.jobhandling.CamundaClientExecutorService;
import io.camunda.client.jobhandling.JobCallbackCommandWrapperFactory;
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.client.metrics.MeteredCamundaClientExecutorService;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.client.spring.configuration.CamundaAutoConfiguration;
import io.camunda.client.spring.configuration.ExecutorServiceConfiguration;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import io.camunda.connector.runtime.annotation.OutboundConnectorObjectMapper;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.MergedAnnotations;

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
    contextRunner.run(
        context -> {
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
  void shouldNotCreateConnectorExecutorServiceWhenVirtualThreadsAreDisabled() {
    contextRunner
        .withPropertyValues("camunda.connector.virtual-threads.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(CONNECTOR_EXECUTOR_BEAN_NAME);
              assertThat(context).hasSingleBean(CamundaClientExecutorService.class);
            });
  }

  static class RequiredOutboundRuntimeBeans {

    @Bean
    CamundaClient camundaClient() {
      return mock(CamundaClient.class);
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
