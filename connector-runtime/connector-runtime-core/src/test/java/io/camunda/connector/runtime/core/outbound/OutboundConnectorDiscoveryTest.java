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

import static io.camunda.connector.runtime.core.testutil.TestUtil.withEnvVars;

import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import java.util.*;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class OutboundConnectorDiscoveryTest {

  @Test
  public void shouldConfigureThroughEnv() throws Exception {

    // given
    var env =
        new Object[] {
          // shall be picked up with meta-data + overrides
          "CONNECTOR_ANNOTATED_OVERRIDE_FUNCTION",
          "io.camunda.connector.runtime.core.outbound.SpiRegisteredFunction",
          "CONNECTOR_ANNOTATED_OVERRIDE_TYPE",
          "io.camunda:annotated-override",
          "CONNECTOR_ANNOTATED_OVERRIDE_TIMEOUT",
          "30000",

          // shall be picked up with meta-data
          "CONNECTOR_ANNOTATED_FUNCTION",
          "io.camunda.connector.runtime.core.outbound.SpiRegisteredFunction",

          // shall be picked up despite no meta-data
          "CONNECTOR_NOT_ANNOTATED_FUNCTION",
          "io.camunda.connector.runtime.core.outbound.NotSpiRegisteredFunction",
          "CONNECTOR_NOT_ANNOTATED_TYPE",
          "io.camunda:not-annotated",
          "CONNECTOR_NOT_ANNOTATED_INPUT_VARIABLES",
          "foo,bar"
        };

    // when
    Collection<OutboundConnectorConfiguration> registrations =
        withEnvVars(env, () -> DiscoveryUtils.getFactory().getConfigurations());

    // then
    Assertions.assertThat(registrations).hasSize(3);

    assertRegistration(
        registrations,
        "ANNOTATED_OVERRIDE",
        "io.camunda:annotated-override",
        new String[] {"a", "b"},
        SpiRegisteredFunction.class.getName(),
        30000L);

    assertRegistration(
        registrations,
        "ANNOTATED",
        "io.camunda:annotated",
        new String[] {"a", "b"},
        SpiRegisteredFunction.class.getName(),
        null);

    assertRegistration(
        registrations,
        "NOT_ANNOTATED",
        "io.camunda:not-annotated",
        new String[] {"foo", "bar"},
        NotSpiRegisteredFunction.class.getName(),
        null);
  }

  @Test
  public void shouldConfigureThroughEnv_FailOnIncompleteConfiguration() {

    // given
    var env =
        new Object[] {
          "CONNECTOR_NOT_ANNOTATED_FUNCTION",
          // This class does not implement the OutboundConnectorFunction interface
          // But since it never gets constructed, it is not a problem
          "io.camunda.connector.runtime.core.outbound.JobBuilder",
          "CONNECTOR_NOT_ANNOTATED_INPUT_VARIABLES",
          "foo,bar"
        };

    // then
    Assertions.assertThatThrownBy(
            () -> withEnvVars(env, () -> DiscoveryUtils.getFactory().getConfigurations()))
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
    Assertions.assertThatThrownBy(
            () -> withEnvVars(env, () -> DiscoveryUtils.getFactory().getConfigurations()))
        .hasMessage("Failed to load io.camunda.connector.runtime.jobworker.impl.outbound.NotFound");
  }

  @Test
  public void shouldConfigureViaSPI() {

    // when
    var registrations = DiscoveryUtils.getFactory().getConfigurations();

    // then
    Assertions.assertThat(registrations).hasSize(1);

    assertRegistration(
        registrations,
        "ANNOTATED",
        "io.camunda:annotated",
        new String[] {"a", "b"},
        SpiRegisteredFunction.class.getName(),
        null);
  }

  @Test
  public void shouldOverrideWhenRegisteredManually() {

    // given SPI configuration and manual registration
    var registry = DiscoveryUtils.getFactory(new NotSpiRegisteredFunction());
    // then
    var registrations = registry.getConfigurations();
    Assertions.assertThat(registrations).hasSize(1);
    assertRegistration(
        registrations,
        "NOT_ANNOTATED",
        "io.camunda:annotated",
        new String[] {"foo", "bar"},
        NotSpiRegisteredFunction.class.getName(),
        null);
  }

  @Test
  public void shouldDisableThroughEnv() throws Exception {
    // given
    var env = new Object[] {"CONNECTOR_OUTBOUND_DISABLED", "io.camunda:annotated"};

    // when
    Collection<OutboundConnectorConfiguration> registrations =
        withEnvVars(env, () -> DiscoveryUtils.getFactory().getConfigurations());

    // then
    Assertions.assertThat(registrations).isEmpty();
  }

  private static void assertRegistration(
      Collection<OutboundConnectorConfiguration> registrations,
      String name,
      String type,
      String[] inputVariables,
      String functionCls,
      Long timeout) {

    Assertions.assertThatCollection(registrations)
        .anySatisfy(
            s -> {
              Assertions.assertThat(s.name()).isEqualTo(name);
              Assertions.assertThat(s.type()).isEqualTo(type);
              Assertions.assertThat(s.inputVariables()).containsExactly(inputVariables);
              Assertions.assertThat(s.instanceSupplier().get().getClass().getName())
                  .isEqualTo(functionCls);
              Assertions.assertThat(s.timeout()).isEqualTo(timeout);
            });
  }
}
