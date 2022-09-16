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
import static org.assertj.core.api.Assertions.catchException;

import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.impl.secrets.*;
import io.camunda.connector.test.ConnectorContextBuilder;
import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.ThrowingConsumer;

class ConnectorContextTest {

  @Nested
  class FieldAccessTests {

    @TestFactory
    public Stream<DynamicTest> shouldGetPropertyFromField() {
      // given
      TestInput testInput = new TestInput();
      Stream<GetPropertyTestInput> input =
          Stream.of(
              new GetPropertyTestInput("publicField", testInput.publicField),
              new GetPropertyTestInput("protectedField", testInput.protectedField),
              new GetPropertyTestInput("privateField", testInput.getPrivateField()),
              new GetPropertyTestInput("finalField", testInput.finalField),
              new GetPropertyTestInput("packageField", testInput.packageField));
      ThrowingConsumer<GetPropertyTestInput> executor =
          in -> {
            // when
            String content =
                AbstractConnectorContext.getProperty(testInput, fieldForName(in.fieldName));
            // then
            assertThat(content).isEqualTo(in.expectedContent);
          };
      return DynamicTest.stream(input, i -> i.fieldName, executor);
    }

    @TestFactory
    public Stream<DynamicTest> shouldSetPropertyToField() {
      // given
      TestInput testInput = new TestInput();
      String modified = "modified";
      Stream<SetPropertyTestInput> input =
          Stream.of(
              new SetPropertyTestInput("publicField", testInput::getPublicField),
              new SetPropertyTestInput("protectedField", testInput::getProtectedField),
              new SetPropertyTestInput("privateField", testInput::getPrivateField),
              new SetPropertyTestInput("packageField", testInput::getPackageField));
      ThrowingConsumer<SetPropertyTestInput> executor =
          in -> {
            // when
            AbstractConnectorContext.setProperty(testInput, fieldForName(in.fieldName), modified);
            // then
            assertThat(in.getter.get()).isEqualTo(modified);
          };
      return DynamicTest.stream(input, i -> i.fieldName, executor);
    }

    @Test
    public void shouldThrowWhenSettingOnFinalField() {
      // given
      TestInput testInput = new TestInput();
      String modified = "modified";
      Exception expected =
          catchException(
              () ->
                  // when
                  AbstractConnectorContext.setProperty(
                      testInput, fieldForName("finalField"), modified));
      // then
      assertThat(expected)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage(
              "Cannot invoke set or setter on final field 'finalField' of type class io.camunda.connector.impl.TestInput");
    }
  }

  @Nested
  class SecretsTests {
    @Test
    void shouldReplaceNestedSecrets() {
      // given
      ConnectorContext connectorContext =
          ConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputNestedObject();
      // when
      connectorContext.replaceSecrets(testInput);
      // then
      assertThat(testInput.secretContainer.getSecretField()).isEqualTo("plain");
      // it does not replace secrets in nested properties that are not marked
      assertThat(testInput.otherProperty.getSecretField()).isEqualTo("secrets.s3cr3t");
    }

    @Test
    void shouldIgnoreAnnotatedNullValues() {
      // given
      ConnectorContext connectorContext =
          ConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputNestedObject();
      // when
      connectorContext.replaceSecrets(testInput);
      // then
      assertThat(testInput.nullContainer).isNull();
    }

    @Test
    void shouldReplaceSecretsInStringList() {
      // given
      ConnectorContext connectorContext =
          ConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputStringList();
      // when
      connectorContext.replaceSecrets(testInput);
      // then
      assertThat(testInput.stringList).allMatch(s -> List.of("plain", "foo").contains(s));
    }

    @Test
    void shouldReplaceSecretsInObjectList() {
      // given
      ConnectorContext connectorContext =
          ConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputObjectList();
      // when
      connectorContext.replaceSecrets(testInput);
      // then
      assertThat(testInput.inputList).extracting("secretField").allMatch("plain"::equals);
    }

    @Test
    void shouldFailIfReplaceSecretsInNumberList() {
      // given
      ConnectorContext connectorContext =
          ConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputNumberList();
      // when
      Exception expected = catchException(() -> connectorContext.replaceSecrets(testInput));
      // then
      assertThat(expected)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Element at index 0 in list has no nested properties and is no String!");
    }

    @Test
    void shouldFailIfReplaceSecretsInImmutableStringList() {
      // given
      ConnectorContext connectorContext =
          ConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputStringListImmutable();
      // when
      Exception expected = catchException(() -> connectorContext.replaceSecrets(testInput));
      // then
      assertThat(expected)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("List is immutable but contains String secrets to replace!");
    }

    @Test
    void shouldReplaceSecretsInObjectSet() {
      // given
      ConnectorContext connectorContext =
          ConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputObjectSet();
      // when
      connectorContext.replaceSecrets(testInput);
      // then
      assertThat(testInput.inputSet).extracting("secretField").allMatch("plain"::equals);
    }

    @Test
    void shouldFailIfReplaceSecretsInStringSet() {
      // given
      ConnectorContext connectorContext =
          ConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputStringSet();
      // when
      Exception expected = catchException(() -> connectorContext.replaceSecrets(testInput));
      // then
      assertThat(expected)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Element in iterable has no nested properties!");
    }

    @Test
    void shouldFailIfReplaceSecretsInNumberSet() {
      // given
      ConnectorContext connectorContext =
          ConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputNumberSet();
      // when
      Exception expected = catchException(() -> connectorContext.replaceSecrets(testInput));
      // then
      assertThat(expected)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Element in iterable has no nested properties!");
    }

    @Test
    void shouldReplaceSecretsInStringArray() {
      // given
      ConnectorContext connectorContext =
          ConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputStringArray();
      // when
      connectorContext.replaceSecrets(testInput);
      // then
      assertThat(testInput.stringArray).allMatch(s -> List.of("plain", "foo").contains(s));
    }

    @Test
    void shouldReplaceSecretsInObjectArray() {
      // given
      ConnectorContext connectorContext =
          ConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputObjectArray();
      // when
      connectorContext.replaceSecrets(testInput);
      // then
      assertThat(testInput.inputArray).extracting("secretField").allMatch("plain"::equals);
    }

    @Test
    void shouldFailIfReplaceSecretsInNumberArray() {
      // given
      ConnectorContext connectorContext =
          ConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputNumberArray();
      // when
      Exception expected = catchException(() -> connectorContext.replaceSecrets(testInput));
      // then
      assertThat(expected)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Element at index 0 in array has no nested properties and is no String!");
    }

    @Test
    void shouldReplaceSecretsInStringMap() {
      // given
      ConnectorContext connectorContext =
          ConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputStringMap();
      // when
      connectorContext.replaceSecrets(testInput);
      // then
      assertThat(testInput.stringMap)
          .allSatisfy((key, value) -> assertThat(List.of("plain", "foo")).contains(value));
    }

    @Test
    void shouldReplaceSecretsInObjectMap() {
      // given
      ConnectorContext connectorContext =
          ConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputObjectMap();
      // when
      connectorContext.replaceSecrets(testInput);
      // then
      assertThat(testInput.inputMap)
          .allSatisfy((key, value) -> assertThat(value.getSecretField()).isEqualTo("plain"));
    }

    @Test
    void shouldReplaceSecretsInImmutableStringMap() {
      // given
      ConnectorContext connectorContext =
          ConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputStringMapImmutable();
      // when
      Exception expected = catchException(() -> connectorContext.replaceSecrets(testInput));
      // then
      assertThat(expected)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Map is immutable but contains String secrets to replace!");
    }

    @Test
    void shouldFailIfReplaceSecretsInNumberMap() {
      // given
      ConnectorContext connectorContext =
          ConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputNumberMap();
      // when
      Exception expected = catchException(() -> connectorContext.replaceSecrets(testInput));
      // then
      assertThat(expected)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Element at key")
          .hasMessageContaining("in map has no nested properties and is no String!");
    }

    @Test
    void shouldReplaceSecretsInRootList() {
      // given
      ConnectorContext connectorContext =
          ConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputObjectList();
      // when
      connectorContext.replaceSecrets(testInput.inputList);
      // then
      assertThat(testInput.inputList).extracting("secretField").allMatch("plain"::equals);
    }

    @Test
    void shouldReplaceSecretsInRootSet() {
      // given
      ConnectorContext connectorContext =
          ConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputObjectSet();
      // when
      connectorContext.replaceSecrets(testInput.inputSet);
      // then
      assertThat(testInput.inputSet).extracting("secretField").allMatch("plain"::equals);
    }

    @Test
    void shouldReplaceSecretsInRootArray() {
      // given
      ConnectorContext connectorContext =
          ConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputObjectArray();
      // when
      connectorContext.replaceSecrets(testInput.inputArray);
      // then
      assertThat(testInput.inputArray).extracting("secretField").allMatch("plain"::equals);
    }

    @Test
    void shouldReplaceSecretsInRootMap() {
      // given
      ConnectorContext connectorContext =
          ConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputObjectMap();
      // when
      connectorContext.replaceSecrets(testInput.inputMap);
      // then
      assertThat(testInput.inputMap)
          .allSatisfy((key, value) -> assertThat(value.getSecretField()).isEqualTo("plain"));
    }
  }

  private static Field fieldForName(String fieldName) {
    try {
      return TestInput.class.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  private static class SetPropertyTestInput {
    private final String fieldName;
    private final Supplier<String> getter;

    public SetPropertyTestInput(String fieldName, Supplier<String> getter) {
      this.fieldName = fieldName;
      this.getter = getter;
    }
  }

  private static class GetPropertyTestInput {
    private final String fieldName;
    private final String expectedContent;

    public GetPropertyTestInput(String fieldName, String expectedContent) {
      this.fieldName = fieldName;
      this.expectedContent = expectedContent;
    }
  }
}
