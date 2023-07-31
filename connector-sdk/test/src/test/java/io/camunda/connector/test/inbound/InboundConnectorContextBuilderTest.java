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
package io.camunda.connector.test.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.error.ConnectorSecretException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

public class InboundConnectorContextBuilderTest {

  @Test
  public void shouldProvidePropertiesAsString() {
    var json = "{ \"foo\" : \"FOO\" }";
    var context = InboundConnectorContextBuilder.create().properties(json).build();
    assertThat(context.getProperties()).isEqualTo(Map.of("foo", "FOO"));
  }

  @Test
  public void shouldProvidePropertiesAsMapAndReplaceSecrets() {
    var properties = Map.of("foo", "{{secrets.FOO}}");
    var context =
        InboundConnectorContextBuilder.create()
            .properties(properties)
            // try replacing secrets to check how it handles immutable maps
            .secret("FOO", "BAR")
            .build();
    assertThat(context.bindProperties(TestRecord.class)).isEqualTo(new TestRecord("BAR"));
  }

  @Test
  public void shouldProvidePropertiesOneByOne() {
    var context =
        InboundConnectorContextBuilder.create()
            .property("record.foo", "FOO")
            .property("bar", "BAR")
            .build();
    assertThat(context.bindProperties(TestWrapperRecord.class))
        .isEqualTo(new TestWrapperRecord(new TestRecord("FOO"), "BAR"));
  }

  @Test
  public void shouldProvidePropertiesAsObject() {
    var context =
        InboundConnectorContextBuilder.create()
            .properties(new TestWrapperRecord(new TestRecord("FOO"), "BAR"))
            .build();
    assertThat(context.bindProperties(TestWrapperRecord.class))
        .isEqualTo(new TestWrapperRecord(new TestRecord("FOO"), "BAR"));
  }

  @Test
  public void shouldThrowOnDuplicatePropertyDefinition() {
    var exception =
        catchException(
            () -> InboundConnectorContextBuilder.create().properties("{ }").properties("{ }"));
    assertThat(exception).hasMessage("Properties already set");
  }

  @Test
  public void shouldProvideSecret() {
    var context =
        InboundConnectorContextBuilder.create().properties("{}").secret("foo", "FOO").build();
    var replaced = context.getSecretHandler().replaceSecrets("secrets.foo");
    assertThat(replaced).isEqualTo("FOO");
  }

  @Test
  public void shouldThrowOnMissingSecret() {
    var context =
        InboundConnectorContextBuilder.create().properties("{}").secret("x", "FOO").build();
    Executable replacement = () -> context.getSecretHandler().replaceSecrets("secrets.foo");
    assertThrows(
        ConnectorSecretException.class, replacement, "Secret with name 'foo' is not available");
  }

  @Test
  public void shouldProvideMultipleSecrets() {
    var context =
        InboundConnectorContextBuilder.create()
            .properties("{}")
            .secret("foo", "FOO")
            .secret("bar", "BAR")
            .build();
    var replaced = context.getSecretHandler().replaceSecrets("secrets.foo secrets.bar");
    assertThat(replaced).isEqualTo("FOO BAR");
  }

  @Test
  public void shouldProvideSecretWithParentheses() {
    var context =
        InboundConnectorContextBuilder.create().properties("{}").secret("foo", "FOO").build();
    var replaced = context.getSecretHandler().replaceSecrets("{{secrets.foo}}");
    assertThat(replaced).isEqualTo("FOO");
  }

  @Test
  public void shouldProvideMultipleSecretWithParentheses() {
    var context =
        InboundConnectorContextBuilder.create()
            .properties("{}")
            .secret("foo", "FOO")
            .secret("bar", "BAR")
            .build();
    var replaced = context.getSecretHandler().replaceSecrets("{{secrets.foo}} {{secrets.bar}}");
    assertThat(replaced).isEqualTo("FOO BAR");
  }

  private record TestRecord(String foo) {}

  private record TestWrapperRecord(TestRecord record, String bar) {}
}
