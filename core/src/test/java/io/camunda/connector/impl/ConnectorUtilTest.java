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
package io.camunda.connector.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.impl.outbound.OutboundConnectorConfiguration;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class ConnectorUtilTest {

  @Nested
  class GetOutboundConnectorConfiguration {

    @Test
    public void shouldRetrieveConnectorConfiguration() {

      // when
      Optional<OutboundConnectorConfiguration> configuration =
          ConnectorUtil.getOutboundConnectorConfiguration(AnnotatedFunction.class);

      // then
      assertThat(configuration)
          .isPresent()
          .hasValueSatisfying(
              config -> {
                assertThat(config.getName()).isEqualTo("ANNOTATED");
                assertThat(config.getType()).isEqualTo("io.camunda.Annotated");
                assertThat(config.getInputVariables()).isEqualTo(new String[] {"FOO"});
              });
    }

    @Test
    public void shouldHandleMissingConnectorConfiguration() {

      // when
      Optional<OutboundConnectorConfiguration> configuration =
          ConnectorUtil.getOutboundConnectorConfiguration(UnannotatedFunction.class);

      // then
      assertThat(configuration).isNotPresent();
    }
  }
}

@OutboundConnector(
    name = "ANNOTATED",
    inputVariables = {"FOO"},
    type = "io.camunda.Annotated")
class AnnotatedFunction {}

class UnannotatedFunction {}
