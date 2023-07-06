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
package io.camunda.connector.test.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.error.ConnectorSecretException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

public class OutboundConnectorContextBuilderTest {

  @Test
  public void shouldProvideVariablesAsString() {
    var json = "{ \"foo\" : \"FOO\" }";
    var context = OutboundConnectorContextBuilder.create().variables(json).build();
    assertThat(context.getVariables()).isEqualTo("{ \"foo\" : \"FOO\" }");
  }

  @Test
  public void shouldThrowOnDuplicateVariableDefinition() {
    var exception =
        catchException(
            () -> OutboundConnectorContextBuilder.create().variables("{ }").variables("{ }"));
    assertThat(exception).hasMessage("variablesAsJSON already set");
  }

  @Test
  public void shouldProvideSecret() {
    var context =
        OutboundConnectorContextBuilder.create().variables("{}").secret("foo", "FOO").build();
    var replaced = context.getSecretHandler().replaceSecrets("secrets.foo");
    assertThat(replaced).isEqualTo("FOO");
  }

  @Test
  public void shouldThrowOnMissingSecret() {
    var context =
        OutboundConnectorContextBuilder.create().variables("{}").secret("x", "FOO").build();
    Executable replacement = () -> context.getSecretHandler().replaceSecrets("secrets.foo");
    assertThrows(
        ConnectorSecretException.class, replacement, "Secret with name 'foo' is not available");
  }

  @Test
  public void shouldProvideMultipleSecrets() {
    var context =
        OutboundConnectorContextBuilder.create()
            .variables("{}")
            .secret("foo", "FOO")
            .secret("bar", "BAR")
            .build();
    var replaced = context.getSecretHandler().replaceSecrets("secrets.foo secrets.bar");
    assertThat(replaced).isEqualTo("FOO BAR");
  }

  @Test
  public void shouldProvideSecretWithParentheses() {
    var context =
        OutboundConnectorContextBuilder.create().variables("{}").secret("foo", "FOO").build();
    var replaced = context.getSecretHandler().replaceSecrets("{{secrets.foo}}");
    assertThat(replaced).isEqualTo("FOO");
  }

  @Test
  public void shouldProvideMultipleSecretWithParentheses() {
    var context =
        OutboundConnectorContextBuilder.create()
            .variables("{}")
            .secret("foo", "FOO")
            .secret("bar", "BAR")
            .build();
    var replaced = context.getSecretHandler().replaceSecrets("{{secrets.foo}} {{secrets.bar}}");
    assertThat(replaced).isEqualTo("FOO BAR");
  }
}
