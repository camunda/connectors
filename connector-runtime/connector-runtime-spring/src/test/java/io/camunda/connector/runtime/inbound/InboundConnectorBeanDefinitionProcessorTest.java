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
package io.camunda.connector.runtime.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.runtime.core.config.InboundConnectorConfiguration;
import io.camunda.connector.runtime.core.inbound.InboundConnectorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@ExtendWith(MockitoExtension.class)
class InboundConnectorBeanDefinitionProcessorTest {

  @Mock private InboundConnectorFactory inboundConnectorFactory;
  @Captor private ArgumentCaptor<InboundConnectorConfiguration> registeredConfigurationCaptor;

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(InboundConnectorFactory.class, () -> inboundConnectorFactory)
          .withUserConfiguration(TestConfig.class, AnnotatedExecutable.class);

  private static class TestConfig {
    @Bean
    public static InboundConnectorBeanDefinitionProcessor inboundConnectorBeanDefinitionProcessor(
        Environment environment) {
      return new InboundConnectorBeanDefinitionProcessor(environment);
    }
  }

  @Test
  void registersInboundConnectorFunctionBean() {
    contextRunner.run(
        context -> {
          verify(inboundConnectorFactory)
              .registerConfiguration(registeredConfigurationCaptor.capture());

          assertThat(registeredConfigurationCaptor.getValue())
              .satisfies(
                  config -> {
                    assertThat(config.name()).isEqualTo("My Executable");
                    assertThat(config.type()).isEqualTo("io.camunda:annotated");
                    assertThat(config.deduplicationProperties()).containsExactly("id");
                    assertThat(config.customInstanceSupplier().get())
                        .isInstanceOf(AnnotatedExecutable.class);
                  });
        });
  }

  @Test
  void overridesType() {
    contextRunner
        .withPropertyValues("CONNECTOR_MY_EXECUTABLE_TYPE=io.camunda:overridden")
        .run(
            context -> {
              verify(inboundConnectorFactory)
                  .registerConfiguration(registeredConfigurationCaptor.capture());

              assertThat(registeredConfigurationCaptor.getValue())
                  .satisfies(
                      config -> {
                        assertThat(config.name()).isEqualTo("My Executable");
                        assertThat(config.type()).isEqualTo("io.camunda:overridden");
                        assertThat(config.deduplicationProperties()).containsExactly("id");
                        assertThat(config.customInstanceSupplier().get())
                            .isInstanceOf(AnnotatedExecutable.class);
                      });
            });
  }
}

@InboundConnector(
    name = "My Executable",
    type = "io.camunda:annotated",
    deduplicationProperties = {"id"})
@Component
@Scope("prototype")
class AnnotatedExecutable implements InboundConnectorExecutable {

  @Override
  public void activate(InboundConnectorContext context) {}

  @Override
  public void deactivate() {}
}
