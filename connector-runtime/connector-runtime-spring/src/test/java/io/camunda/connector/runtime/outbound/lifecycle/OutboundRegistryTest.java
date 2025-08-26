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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.DefaultOutboundConnectorFactory;
import io.camunda.connector.runtime.core.validation.ValidationUtil;
import io.camunda.connector.runtime.outbound.OutboundConnectorRuntimeConfiguration;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

class OutboundRegistryTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class, AnnotatedFunction.class);

  private static class TestConfig {
    @Bean
    public DefaultOutboundConnectorFactory outboundFactory(
        Environment environment,
        List<OutboundConnectorFunction> functions,
        List<OutboundConnectorProvider> providers) {
      return (new OutboundConnectorRuntimeConfiguration())
          .outboundConnectorConfigurationRegistry(
              new ObjectMapper(),
              ValidationUtil.discoverDefaultValidationProviderImplementation(),
              environment,
              functions,
              providers);
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
          assertThat(config.instanceSupplier().get()).isInstanceOf(AnnotatedFunction.class);
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
          assertThat(config.instanceSupplier().get()).isInstanceOf(AnnotatedFunction.class);
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
          assertThat(config.instanceSupplier().get()).isInstanceOf(AnnotatedFunction.class);
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
          assertThat(config.instanceSupplier().get()).isInstanceOf(AnnotatedFunction.class);
        });
  }

  private void runTest(
      ApplicationContextRunner configuredContextRunner,
      ThrowingConsumer<OutboundConnectorConfiguration> configurationAssertions) {
    configuredContextRunner.run(
        context -> {
          var outboundConnectorRegistry = context.getBean(DefaultOutboundConnectorFactory.class);
          Assertions.assertThatStream(outboundConnectorRegistry.getConfigurations().stream())
              .anyMatch(
                  e -> {
                    try {
                      configurationAssertions.accept(e);
                      return true;
                    } catch (Exception ex) {
                      return false;
                    }
                  });
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
