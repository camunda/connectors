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
package io.camunda.connector.runtime.outbound.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.camunda.client.CamundaClient;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@ExtendWith(MockitoExtension.class)
class OutboundConnectorAnnotationProcessorTest {

  @Mock private CamundaClient camundaClient;
  @Mock private OutboundConnectorManager outboundConnectorManager;
  @Mock private OutboundConnectorFactory outboundConnectorFactory;

  @Captor private ArgumentCaptor<OutboundConnectorConfiguration> registeredConfigurationCaptor;

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(CamundaClient.class, () -> camundaClient)
          .withBean(OutboundConnectorManager.class, () -> outboundConnectorManager)
          .withBean(OutboundConnectorFactory.class, () -> outboundConnectorFactory)
          .withUserConfiguration(TestConfig.class, AnnotatedFunction.class);

  private static class TestConfig {
    @Bean
    public OutboundConnectorAnnotationProcessor annotationProcessor(
        Environment environment,
        OutboundConnectorManager manager,
        OutboundConnectorFactory factory) {
      return new OutboundConnectorAnnotationProcessor(manager);
    }
  }

  @Test
  void registersOutboundConnectorFunctionBean() {
    runTest(
        contextRunner,
        config -> {
          assertThat(config.name()).isEqualTo("My Function");
          assertThat(config.type()).isEqualTo("io.camunda:annotated");
          assertThat(config.inputVariables()).isEqualTo(new String[] {"a", "b"});
          assertThat(config.timeout()).isNull();
          assertThat(config.customInstanceSupplier().get()).isInstanceOf(AnnotatedFunction.class);
        });
  }

  @Test
  void overridesType() {
    runTest(
        contextRunner.withPropertyValues("CONNECTOR_MY_FUNCTION_TYPE=io.camunda:overridden"),
        config -> {
          assertThat(config.name()).isEqualTo("My Function");
          assertThat(config.type()).isEqualTo("io.camunda:overridden");
          assertThat(config.inputVariables()).isEqualTo(new String[] {"a", "b"});
          assertThat(config.timeout()).isNull();
          assertThat(config.customInstanceSupplier().get()).isInstanceOf(AnnotatedFunction.class);
        });
  }

  @Test
  void overridesTimeout() {
    runTest(
        contextRunner.withPropertyValues("CONNECTOR_MY_FUNCTION_TIMEOUT=123456"),
        config -> {
          assertThat(config.name()).isEqualTo("My Function");
          assertThat(config.type()).isEqualTo("io.camunda:annotated");
          assertThat(config.inputVariables()).isEqualTo(new String[] {"a", "b"});
          assertThat(config.timeout()).isEqualTo(123456L);
          assertThat(config.customInstanceSupplier().get()).isInstanceOf(AnnotatedFunction.class);
        });
  }

  @Test
  void overridesTypeAndTimeout() {
    runTest(
        contextRunner.withPropertyValues(
            "CONNECTOR_MY_FUNCTION_TYPE=io.camunda:overridden",
            "CONNECTOR_MY_FUNCTION_TIMEOUT=123456"),
        config -> {
          assertThat(config.name()).isEqualTo("My Function");
          assertThat(config.type()).isEqualTo("io.camunda:overridden");
          assertThat(config.inputVariables()).isEqualTo(new String[] {"a", "b"});
          assertThat(config.timeout()).isEqualTo(123456L);
          assertThat(config.customInstanceSupplier().get()).isInstanceOf(AnnotatedFunction.class);
        });
  }

  private void runTest(
      ApplicationContextRunner configuredContextRunner,
      ThrowingConsumer<OutboundConnectorConfiguration> configurationAssertions) {
    configuredContextRunner.run(
        context -> {
          context.getBean(OutboundConnectorAnnotationProcessor.class).onStart(camundaClient);

          verify(outboundConnectorFactory)
              .registerConfiguration(registeredConfigurationCaptor.capture());

          assertThat(registeredConfigurationCaptor.getValue()).satisfies(configurationAssertions);
        });
  }
}

@OutboundConnector(
    name = "My Function",
    inputVariables = {"a", "b"},
    type = "io.camunda:annotated")
@Component
class AnnotatedFunction implements OutboundConnectorFunction {
  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    return null;
  }
}
