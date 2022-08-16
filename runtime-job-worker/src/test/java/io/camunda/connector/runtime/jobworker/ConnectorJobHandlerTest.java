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

package io.camunda.connector.runtime.jobworker;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.ConnectorFunction;
import io.camunda.connector.api.SecretProvider;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class ConnectorJobHandlerTest {

  @Nested
  class Secrets {

    @Test
    public void shouldReplaceSecretsViaSpiLoadedProvider() throws Exception {
      // given
      var jobHandler =
          new ConnectorJobHandler(
              (context) -> {
                return context
                    .getSecretStore()
                    .replaceSecret("secrets." + TestSecretProvider.SECRET_NAME);
              });

      // when
      var result = JobBuilder.create().withResultHeader("result").execute(jobHandler);

      // then
      assertThat(result.getVariable("result")).isEqualTo(TestSecretProvider.SECRET_VALUE);
    }

    @Test
    public void shouldOverrideSecretProvider() {
      // given
      var jobHandler =
          new TestConnectorJobHandler(
              (context) -> {
                return context
                    .getSecretStore()
                    .replaceSecret("secrets." + TestSecretProvider.SECRET_NAME);
              });

      // when
      var result = JobBuilder.create().withResultHeader("result").execute(jobHandler);

      // then
      assertThat(result.getVariable("result")).isEqualTo("baz");
    }
  }

  @Nested
  class Output {

    @Test
    public void shouldNotSetWithoutResultVariable() throws Exception {

      // given
      var jobHandler =
          new ConnectorJobHandler(
              (context) -> {
                return Map.of("hello", "world");
              });

      // when
      var result = JobBuilder.create().execute(jobHandler);

      // then
      assertThat(result.getVariables()).isEmpty();
    }

    @Test
    public void shouldSetToResultVariable() throws Exception {

      // given
      var jobHandler =
          new ConnectorJobHandler(
              (context) -> {
                return Map.of("hello", "world");
              });

      // when
      var result = JobBuilder.create().withResultHeader("result").execute(jobHandler);

      // then
      assertThat(result.getVariables()).isEqualTo(Map.of("result", Map.of("hello", "world")));
    }
  }

  private static class TestConnectorJobHandler extends ConnectorJobHandler {

    public TestConnectorJobHandler(ConnectorFunction call) {
      super(call);
    }

    @Override
    public SecretProvider getSecretProvider() {
      return name -> TestSecretProvider.SECRET_NAME.equals(name) ? "baz" : null;
    }
  }
}
