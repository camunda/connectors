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
package io.camunda.connector.runtime.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.runtime.core.config.InboundConnectorConfiguration;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
public class ConnectorUtilTest {

  @Nested
  class GetOutboundConnectorConfiguration {

    @Test
    public void shouldRetrieveConnectorConfiguration() {

      // when
      OutboundConnectorConfiguration configuration =
          ConnectorUtil.getOutboundConnectorConfiguration(AnnotatedFunction.class);

      // then
      assertThat(configuration)
          .satisfies(
              config -> {
                assertThat(config.name()).isEqualTo("ANNOTATED");
                assertThat(config.type()).isEqualTo("io.camunda.Annotated");
                assertThat(config.inputVariables()).isEqualTo(new String[] {"FOO"});
                assertThat(config.timeout()).isNull();
              });
    }

    @Test
    public void shouldHandleMissingConnectorConfiguration() {

      // when
      Assertions.assertThrows(
          RuntimeException.class,
          () -> ConnectorUtil.getOutboundConnectorConfiguration(UnannotatedFunction.class));
    }

    @Test
    void shouldOverrideTypeAndTimeout() throws Exception {
      EnvironmentVariables environmentVariables =
          new EnvironmentVariables(
              "CONNECTOR_ANNOTATED_TYPE", "io.camunda:connector:XXXXXXX",
              "CONNECTOR_ANNOTATED_TIMEOUT", "123456");

      environmentVariables.execute(
          () -> {
            OutboundConnectorConfiguration configuration =
                ConnectorUtil.getOutboundConnectorConfiguration(AnnotatedFunction.class);
            assertThat(configuration.type()).isEqualTo("io.camunda:connector:XXXXXXX");
            assertThat(configuration.timeout()).isEqualTo(123456L);
          });
    }

    @Test
    void shouldNormalizeOutboundConnectorNameWithoutOverride() {
      // given
      @OutboundConnector(
          name = "NonNormalized nAme",
          inputVariables = {"foo"},
          type = "io.camunda:connector:1")
      class NonNormalizedOutboundConnector implements OutboundConnectorFunction {
        @Override
        public Object execute(OutboundConnectorContext context) throws Exception {
          return null;
        }
      }

      // when
      OutboundConnectorConfiguration configuration =
          ConnectorUtil.getOutboundConnectorConfiguration(NonNormalizedOutboundConnector.class);

      // then
      assertThat(configuration.type()).isEqualTo("io.camunda:connector:1");
    }

    @Test
    void shouldNormalizeOutboundConnectorNameWithOverride() throws Exception {
      // given
      @OutboundConnector(
          name = "NonNormalized nAme",
          inputVariables = {"foo"},
          type = "io.camunda:connector:1")
      class NonNormalizedOutboundConnector implements OutboundConnectorFunction {
        @Override
        public Object execute(OutboundConnectorContext context) throws Exception {
          return null;
        }
      }

      // when
      EnvironmentVariables environmentVariables =
          new EnvironmentVariables(
              "CONNECTOR_NONNORMALIZED_NAME_TYPE", "io.camunda:connector:XXXXXXX");
      environmentVariables.execute(
          () -> {
            OutboundConnectorConfiguration configuration =
                ConnectorUtil.getOutboundConnectorConfiguration(
                    NonNormalizedOutboundConnector.class);
            // then
            assertThat(configuration.type()).isEqualTo("io.camunda:connector:XXXXXXX");
          });
    }

    @Test
    void shouldNotNormalizeOutboundConnectorNameWithoutOverride() {
      // given
      @OutboundConnector(
          name = "MY_CONNECTOR",
          inputVariables = {"foo"},
          type = "io.camunda:connector:1")
      class NonNormalizedOutboundConnector implements OutboundConnectorFunction {
        @Override
        public Object execute(OutboundConnectorContext context) throws Exception {
          return null;
        }
      }

      // when
      OutboundConnectorConfiguration configuration =
          ConnectorUtil.getOutboundConnectorConfiguration(NonNormalizedOutboundConnector.class);

      // then
      assertThat(configuration.name()).isEqualTo("MY_CONNECTOR");
      assertThat(configuration.type()).isEqualTo("io.camunda:connector:1");
    }

    @Test
    void shouldNotNormalizeOutboundConnectorNameWithOverride() throws Exception {
      // given
      @OutboundConnector(
          name = "MY_CONNECTOR",
          inputVariables = {"foo"},
          type = "io.camunda:connector:1")
      class NonNormalizedOutboundConnector implements OutboundConnectorFunction {
        @Override
        public Object execute(OutboundConnectorContext context) throws Exception {
          return null;
        }
      }

      // when
      EnvironmentVariables environmentVariables =
          new EnvironmentVariables("CONNECTOR_MY_CONNECTOR_TYPE", "io.camunda:connector:XXXXXXX");
      environmentVariables.execute(
          () -> {
            OutboundConnectorConfiguration configuration =
                ConnectorUtil.getOutboundConnectorConfiguration(
                    NonNormalizedOutboundConnector.class);
            // then
            assertThat(configuration.name()).isEqualTo("MY_CONNECTOR");
            assertThat(configuration.type()).isEqualTo("io.camunda:connector:XXXXXXX");
          });
    }
  }

  @Nested
  class GetInboundConnectorConfiguration {

    @Test
    public void shouldRetrieveConnectorConfiguration() {

      // when
      InboundConnectorConfiguration configuration =
          ConnectorUtil.getInboundConnectorConfiguration(AnnotatedExecutable.class);

      // then
      assertThat(configuration)
          .satisfies(
              config -> {
                assertThat(config.name()).isEqualTo("ANNOTATED");
                assertThat(config.type()).isEqualTo("io.camunda.Annotated");
                assertThat(config.deduplicationProperties()).containsExactly("id");
              });
    }

    @Test
    public void shouldHandleMissingConnectorConfiguration() {
      Assertions.assertThrows(
          RuntimeException.class,
          () -> ConnectorUtil.getInboundConnectorConfiguration(UnannotatedExecutable.class));
    }

    @Test
    void shouldOverrideType() throws Exception {
      EnvironmentVariables environmentVariables =
          new EnvironmentVariables("CONNECTOR_ANNOTATED_TYPE", "io.camunda:connector:XXXXXXX");

      environmentVariables.execute(
          () -> {
            InboundConnectorConfiguration configuration =
                ConnectorUtil.getInboundConnectorConfiguration(AnnotatedExecutable.class);
            assertThat(configuration.type()).isEqualTo("io.camunda:connector:XXXXXXX");
          });
    }

    @Test
    void shouldNormalizeInboundConnectorNameWithoutOverride() {
      // given
      @InboundConnector(name = "NonNormalized nAme", type = "io.camunda:connector:1")
      class NonNormalizedInboundConnector implements InboundConnectorExecutable {
        @Override
        public void activate(InboundConnectorContext context) throws Exception {}

        @Override
        public void deactivate() throws Exception {}
      }

      // when
      InboundConnectorConfiguration configuration =
          ConnectorUtil.getInboundConnectorConfiguration(NonNormalizedInboundConnector.class);

      // then
      assertThat(configuration.type()).isEqualTo("io.camunda:connector:1");
    }

    @Test
    void shouldNormalizeInboundConnectorNameWithOverride() throws Exception {
      // given
      @InboundConnector(name = "NonNormalized nAme", type = "io.camunda:connector:1")
      class NonNormalizedOutboundConnector implements InboundConnectorExecutable {

        @Override
        public void activate(InboundConnectorContext context) throws Exception {}

        @Override
        public void deactivate() throws Exception {}
      }

      // when
      EnvironmentVariables environmentVariables =
          new EnvironmentVariables(
              "CONNECTOR_NONNORMALIZED_NAME_TYPE", "io.camunda:connector:XXXXXXX");
      environmentVariables.execute(
          () -> {
            InboundConnectorConfiguration configuration =
                ConnectorUtil.getInboundConnectorConfiguration(
                    NonNormalizedOutboundConnector.class);
            // then
            assertThat(configuration.type()).isEqualTo("io.camunda:connector:XXXXXXX");
          });
    }
  }
}

@OutboundConnector(
    name = "ANNOTATED",
    inputVariables = {"FOO"},
    type = "io.camunda.Annotated")
class AnnotatedFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) {
    return null;
  }
}

class UnannotatedFunction implements OutboundConnectorFunction {
  @Override
  public Object execute(OutboundConnectorContext context) {
    return null;
  }
}

@InboundConnector(
    name = "ANNOTATED",
    type = "io.camunda.Annotated",
    deduplicationProperties = {"id"})
class AnnotatedExecutable implements InboundConnectorExecutable {

  @Override
  public void activate(InboundConnectorContext context) {}

  @Override
  public void deactivate() {}
}

class UnannotatedExecutable implements InboundConnectorExecutable {

  @Override
  public void activate(InboundConnectorContext context) {}

  @Override
  public void deactivate() {}
}
