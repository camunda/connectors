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
package io.camunda.connector.runtime.core.outbound;

import static io.camunda.connector.runtime.core.util.TestUtil.withEnvVars;

import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class OutboundConnectorDiscoveryTest {

  private static DefaultOutboundConnectorFactory getFactory() {
    return new DefaultOutboundConnectorFactory();
  }

  @Test
  public void shouldConfigureThroughEnv() throws Exception {

    // given
    var env =
        new Object[] {
          // shall be picked up with meta-data + overrides
          "CONNECTOR_ANNOTATED_OVERRIDE_FUNCTION",
          "io.camunda.connector.runtime.core.outbound.AnnotatedFunction",
          "CONNECTOR_ANNOTATED_OVERRIDE_TYPE",
          "io.camunda:annotated-override",

          // shall be picked up with meta-data
          "CONNECTOR_ANNOTATED_FUNCTION",
          "io.camunda.connector.runtime.core.outbound.AnnotatedFunction",

          // shall be picked up despite no meta-data
          "CONNECTOR_NOT_ANNOTATED_FUNCTION",
          "io.camunda.connector.runtime.core.outbound.NotAnnotatedFunction",
          "CONNECTOR_NOT_ANNOTATED_TYPE",
          "io.camunda:not-annotated",
          "CONNECTOR_NOT_ANNOTATED_INPUT_VARIABLES",
          "foo,bar"
        };

    // when
    List<OutboundConnectorConfiguration> registrations =
        withEnvVars(env, () -> getFactory().getConfigurations());

    // then
    Assertions.assertThat(registrations).hasSize(3);

    assertRegistration(
        registrations,
        "ANNOTATED_OVERRIDE",
        "io.camunda:annotated-override",
        new String[] {"a", "b"},
        AnnotatedFunction.class.getName());

    assertRegistration(
        registrations,
        "ANNOTATED",
        "io.camunda:annotated",
        new String[] {"a", "b"},
        AnnotatedFunction.class.getName());

    assertRegistration(
        registrations,
        "NOT_ANNOTATED",
        "io.camunda:not-annotated",
        new String[] {"foo", "bar"},
        NotAnnotatedFunction.class.getName());
  }

  @Test
  public void shouldConfigureThroughEnv_FailOnIncompleteConfiguration() {

    // given
    var env =
        new Object[] {
          "CONNECTOR_NOT_ANNOTATED_FUNCTION",
          "io.camunda.connector.runtime.core.outbound.NotAnnotatedFunction",
          "CONNECTOR_NOT_ANNOTATED_INPUT_VARIABLES",
          "foo,bar"
        };

    // then
    Assertions.assertThatThrownBy(() -> withEnvVars(env, () -> getFactory().getConfigurations()))
        .hasMessage(
            "Type not specified: Please configure it via CONNECTOR_NOT_ANNOTATED_TYPE environment variable");
  }

  @Test
  public void shouldConfigureThroughEnv_FailOnClassNotFound() {

    // given
    var env =
        new Object[] {
          "CONNECTOR_NOT_FOUND_FUNCTION",
          "io.camunda.connector.runtime.jobworker.impl.outbound.NotFound"
        };

    // then
    Assertions.assertThatThrownBy(() -> withEnvVars(env, () -> getFactory().getConfigurations()))
        .hasMessage("Failed to load io.camunda.connector.runtime.jobworker.impl.outbound.NotFound");
  }

  @Test
  public void shouldConfigureViaSPI() {

    // when
    List<OutboundConnectorConfiguration> registrations = getFactory().getConfigurations();

    // then
    Assertions.assertThat(registrations).hasSize(1);

    assertRegistration(
        registrations,
        "ANNOTATED",
        "io.camunda:annotated",
        new String[] {"a", "b"},
        AnnotatedFunction.class.getName());
  }

  @Test
  public void shouldOverrideWhenRegisteredManually() {

    // given SPI configuration
    DefaultOutboundConnectorFactory factory = getFactory();

    // when
    factory.registerConfiguration(
        new OutboundConnectorConfiguration(
            "ANNOTATED",
            new String[] {"foo", "bar"},
            "io.camunda:annotated",
            NotAnnotatedFunction.class));

    // then
    var registrations = factory.getConfigurations();
    Assertions.assertThat(registrations).hasSize(1);
    assertRegistration(
        registrations,
        "ANNOTATED",
        "io.camunda:annotated",
        new String[] {"foo", "bar"},
        NotAnnotatedFunction.class.getName());
  }

  private static void assertRegistration(
      List<OutboundConnectorConfiguration> registrations,
      String name,
      String type,
      String[] inputVariables,
      String functionCls) {

    Assertions.assertThatList(registrations)
        .anyMatch(
            registration ->
                (registration.name().equals(name)
                    && registration.type().equals(type)
                    && Arrays.equals(registration.inputVariables(), inputVariables)
                    && registration.connectorClass().getName().equals(functionCls)));
  }
}
