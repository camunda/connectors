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

import java.util.HashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class OutboundConnectorContextBuilderTest {

  @Test
  public void shouldProvideVariablesAsObject() {

    // given
    var obj = new Object();

    // when
    var context = OutboundConnectorContextBuilder.create().variables(obj).build();

    // then
    assertThat(context.getVariablesAsType(Object.class)).isEqualTo(obj);
  }

  @Test
  public void shouldProvideVariablesAsObject_failIfNotProvided() {

    // given
    var context = OutboundConnectorContextBuilder.create().build();

    // when
    var exception = catchException(() -> context.getVariablesAsType(Object.class));

    // then
    assertThat(exception).hasMessage("variablesAsObject not provided");
  }

  @Test
  public void shouldProvideVariablesAsObject_failIfIncompatible() {

    // given
    var set = new HashSet<String>();

    var context = OutboundConnectorContextBuilder.create().variables(set).build();

    // when
    var exception = catchException(() -> context.getVariablesAsType(Map.class));

    // then
    assertThat(exception).hasMessage("no variablesAsObject of type java.util.Map provided");
  }

  @Test
  public void shouldProvideVariablesAsString() {

    // given
    var json = "{ \"foo\" : \"FOO\" }";

    // when
    var context = OutboundConnectorContextBuilder.create().variables(json).build();

    // then
    assertThat(context.getVariables()).isEqualTo(json);
  }

  @Test
  public void shouldProvideVariablesAsString_failIfNotProvided() {

    // given
    var context = OutboundConnectorContextBuilder.create().build();

    // when
    var exception = catchException(context::getVariables);

    // then
    assertThat(exception).hasMessage("variablesAsJSON not provided");
  }

  @Test
  public void shouldThrowOnConflictingVariableDefinitions_jsonVariablesAlreadySet() {

    // when
    var exception =
        catchException(
            () ->
                OutboundConnectorContextBuilder.create().variables("{ }").variables(new Object()));

    // then
    assertThat(exception).hasMessage("variablesAsJSON already set");
  }

  @Test
  public void shouldThrowOnConflictingVariableDefinitions_objectVariablesAlreadySet() {

    // when
    var exception =
        catchException(
            () ->
                OutboundConnectorContextBuilder.create().variables(new Object()).variables("{ }"));

    // then
    assertThat(exception).hasMessage("variablesAsObject already set");
  }

  @Test
  public void shouldThrowOnDuplicateVariableDefinition() {

    // when
    var exception =
        catchException(
            () -> OutboundConnectorContextBuilder.create().variables("{ }").variables("{ }"));

    // then
    assertThat(exception).hasMessage("variablesAsJSON already set");
  }

  @Test
  public void shouldProvideSecret() {

    // given
    var context = OutboundConnectorContextBuilder.create().secret("foo", "FOO").build();

    // when
    var replaced = context.getSecretHandler().replaceSecret("secrets.foo");

    // then
    assertThat(replaced).isEqualTo("FOO");
  }

  @Test
  public void shouldProvideSecret_failIfNotProvided() {

    // given
    var context = OutboundConnectorContextBuilder.create().build();

    // when
    var exception = catchException(() -> context.getSecretHandler().replaceSecret("secrets.foo"));

    // then
    assertThat(exception).hasMessage("Secret with name 'foo' is not available");
  }
}
