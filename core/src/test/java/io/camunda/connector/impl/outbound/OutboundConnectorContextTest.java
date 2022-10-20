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
package io.camunda.connector.impl.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.secret.SecretContainerHandler;
import io.camunda.connector.api.secret.SecretElementHandler;
import io.camunda.connector.impl.ReflectionHelper;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.ThrowingConsumer;

class OutboundConnectorContextTest {

  @Nested
  class FieldAccessTests {

    @TestFactory
    public Stream<DynamicTest> shouldGetPropertyFromField() {
      // given
      OutboundTestInput testInput = new OutboundTestInput();
      Stream<GetPropertyTestInput> input =
          Stream.of(
              new GetPropertyTestInput("publicField", testInput.publicField),
              new GetPropertyTestInput("protectedField", testInput.protectedField),
              new GetPropertyTestInput("privateField", testInput.getPrivateField()),
              new GetPropertyTestInput("finalField", testInput.finalField),
              new GetPropertyTestInput("packageField", testInput.packageField),
              new GetPropertyTestInput("staticField", OutboundTestInput.staticField),
              new GetPropertyTestInput(
                  "privateStaticField", OutboundTestInput.getPrivateStaticField()));
      ThrowingConsumer<GetPropertyTestInput> executor =
          in -> {
            // when
            String content = ReflectionHelper.getProperty(testInput, fieldForName(in.fieldName));
            // then
            assertThat(content).isEqualTo(in.expectedContent);
          };
      return DynamicTest.stream(input, i -> i.fieldName, executor);
    }

    @TestFactory
    public Stream<DynamicTest> shouldSetPropertyToField() {
      // given
      OutboundTestInput testInput = new OutboundTestInput();
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
            ReflectionHelper.setProperty(testInput, fieldForName(in.fieldName), modified);
            // then
            assertThat(in.getter.get()).isEqualTo(modified);
          };
      return DynamicTest.stream(input, i -> i.fieldName, executor);
    }

    @Test
    public void shouldThrowWhenSettingOnFinalField() {
      // given
      OutboundTestInput testInput = new OutboundTestInput();
      String modified = "modified";
      Exception expected =
          catchException(
              () ->
                  // when
                  ReflectionHelper.setProperty(testInput, fieldForName("finalField"), modified));
      // then
      assertThat(expected)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage(
              "Cannot invoke set or setter on final field 'finalField' of type class "
                  + testInput.getClass().getName());
    }

    @Test
    public void shouldThrowWhenSettingOnStaticField() {
      // given
      OutboundTestInput testInput = new OutboundTestInput();
      String modified = "modified";
      Exception expected =
          catchException(
              () ->
                  // when
                  ReflectionHelper.setProperty(testInput, fieldForName("staticField"), modified));
      // then
      assertThat(expected)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage(
              "Cannot invoke set or setter on static field 'staticField' of type class "
                  + testInput.getClass().getName());
    }

    @Test
    public void shouldThrowWhenSettingOnPrivateStaticField() {
      // given
      OutboundTestInput testInput = new OutboundTestInput();
      String modified = "modified";
      Exception expected =
          catchException(
              () ->
                  // when
                  ReflectionHelper.setProperty(
                      testInput, fieldForName("privateStaticField"), modified));
      // then
      assertThat(expected)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage(
              "Cannot invoke set or setter on static field 'privateStaticField' of type class "
                  + testInput.getClass().getName());
    }
  }

  @Nested
  class SecretsTests {
    @Test
    void shouldReplaceNestedSecrets() {
      // given
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
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
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputNestedObject();
      // when
      connectorContext.replaceSecrets(testInput);
      // then
      assertThat(testInput.nullContainer).isNull();
    }

    @Test
    void shouldReplaceSecretsInStringList() {
      // given
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputStringList();
      // when
      connectorContext.replaceSecrets(testInput);
      // then
      assertThat(testInput.stringList).allMatch(s -> List.of("plain", "foo").contains(s));
    }

    @Test
    void shouldReplaceSecretsInObjectList() {
      // given
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputObjectList();
      // when
      connectorContext.replaceSecrets(testInput);
      // then
      assertThat(testInput.inputList).extracting("secretField").allMatch("plain"::equals);
    }

    @Test
    void shouldFailIfReplaceSecretsInNumberList() {
      // given
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
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
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
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
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputObjectSet();
      // when
      connectorContext.replaceSecrets(testInput);
      // then
      assertThat(testInput.inputSet).extracting("secretField").allMatch("plain"::equals);
    }

    @Test
    void shouldFailIfReplaceSecretsInStringSet() {
      // given
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
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
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
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
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputStringArray();
      // when
      connectorContext.replaceSecrets(testInput);
      // then
      assertThat(testInput.stringArray).allMatch(s -> List.of("plain", "foo").contains(s));
    }

    @Test
    void shouldReplaceSecretsInObjectArray() {
      // given
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputObjectArray();
      // when
      connectorContext.replaceSecrets(testInput);
      // then
      assertThat(testInput.inputArray).extracting("secretField").allMatch("plain"::equals);
    }

    @Test
    void shouldFailIfReplaceSecretsInNumberArray() {
      // given
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
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
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
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
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputObjectMap();
      // when
      connectorContext.replaceSecrets(testInput);
      // then
      assertThat(testInput.inputMap)
          .allSatisfy((key, value) -> assertThat(value.getSecretField()).isEqualTo("plain"));
    }

    @Test
    void shouldFailIfReplaceSecretsInImmutableStringMap() {
      // given
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
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
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
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
    void shouldFailIfReplaceSecretsInStaticString() {
      // given
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputStaticString();
      // when
      Exception expected = catchException(() -> connectorContext.replaceSecrets(testInput));
      // then
      assertThat(expected)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot invoke set or setter on static field");
    }

    @Test
    void shouldFailIfReplaceSecretsInPrivateStaticString() {
      // given
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputPrivateStaticString();
      // when
      Exception expected = catchException(() -> connectorContext.replaceSecrets(testInput));
      // then
      assertThat(expected)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot invoke set or setter on static field");
    }

    @Test
    void shouldReplaceSecretsInRootList() {
      // given
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputObjectList();
      // when
      connectorContext.replaceSecrets(testInput.inputList);
      // then
      assertThat(testInput.inputList).extracting("secretField").allMatch("plain"::equals);
    }

    @Test
    void shouldReplaceSecretsInRootSet() {
      // given
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputObjectSet();
      // when
      connectorContext.replaceSecrets(testInput.inputSet);
      // then
      assertThat(testInput.inputSet).extracting("secretField").allMatch("plain"::equals);
    }

    @Test
    void shouldReplaceSecretsInRootArray() {
      // given
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputObjectArray();
      // when
      connectorContext.replaceSecrets(testInput.inputArray);
      // then
      assertThat(testInput.inputArray).extracting("secretField").allMatch("plain"::equals);
    }

    @Test
    void shouldReplaceSecretsInRootMap() {
      // given
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputObjectMap();
      // when
      connectorContext.replaceSecrets(testInput.inputMap);
      // then
      assertThat(testInput.inputMap)
          .allSatisfy((key, value) -> assertThat(value.getSecretField()).isEqualTo("plain"));
    }

    @Test
    void shouldReplaceSecretsStringSetWithCustomHandler() {
      // given
      OutboundConnectorContext connectorContext =
          OutboundConnectorContextBuilder.create().secret("s3cr3t", "plain").build();
      final var testInput = new InputStringSetCustomHandling();
      // when
      connectorContext.replaceSecrets(testInput);
      // then
      assertThat(testInput.objectSet)
          .allMatch(
              s -> "plain".equals(s) || "plain".equals(((OutboundTestInput) s).getSecretField()));
    }
  }

  private static Field fieldForName(String fieldName) {
    try {
      return OutboundTestInput.class.getDeclaredField(fieldName);
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

  public static class InputNestedObject {
    @Secret public final OutboundTestInput secretContainer = new OutboundTestInput();
    @Secret public final OutboundTestInput nullContainer = null;
    public final OutboundTestInput otherProperty = new OutboundTestInput();
  }

  public static class InputNumberArray {
    @Secret public Number[] numberArray = new Number[] {3, 5.6f};
  }

  public static class InputNumberList {
    @Secret public List<Number> numberList = new ArrayList<>(List.of(3, 5.6f));
  }

  public static class InputNumberMap {
    @Secret public Map<String, Number> numberMap = new HashMap<>(Map.of("bar", 3, "baz", 5.6f));
  }

  public static class InputNumberSet {
    @Secret public Set<Number> numberSet = new HashSet<>(Set.of(3, 5.6f));
  }

  public static class InputObjectArray {
    @Secret
    public final OutboundTestInput[] inputArray =
        new OutboundTestInput[] {new OutboundTestInput(), new OutboundTestInput()};
  }

  public static class InputObjectList {
    @Secret
    public final List<OutboundTestInput> inputList =
        List.of(new OutboundTestInput(), new OutboundTestInput());
  }

  public static class InputObjectMap {
    @Secret
    public final Map<String, OutboundTestInput> inputMap = Map.of("bar", new OutboundTestInput());
  }

  public static class InputObjectSet {
    @Secret
    public final Set<OutboundTestInput> inputSet =
        Set.of(new OutboundTestInput(), new OutboundTestInput());
  }

  public static class InputStringArray {
    @Secret public final String[] stringArray = new String[] {"secrets.s3cr3t", "foo"};
  }

  public static class InputStringList {
    @Secret
    public final List<String> stringList = new ArrayList<>(List.of("secrets.s3cr3t", "foo"));
  }

  public static class InputStringListImmutable {
    @Secret public List<String> stringSet = List.of("secrets.s3cr3t", "foo");
  }

  public static class InputStringMap {
    @Secret
    public final Map<String, String> stringMap =
        new HashMap<>(Map.of("bar", "secrets.s3cr3t", "baz", "foo"));
  }

  public static class InputStringMapImmutable {
    @Secret public Map<String, String> stringMap = Map.of("foo", "secrets.s3cr3t");
  }

  public static class InputStringSet {
    @Secret public Set<String> stringSet = new HashSet<>(Set.of("secrets.s3cr3t", "foo"));
  }

  public static class InputStaticString {
    @Secret public static String staticString = "secrets.s3cr3t";
  }

  public static class InputStringSetCustomHandling {
    @Secret(handler = MySetHandler.class)
    public Set<Object> objectSet = new HashSet<>(Set.of("secrets.s3cr3t", new OutboundTestInput()));
  }

  public static class InputPrivateStaticString {
    @Secret private static String staticString = "secrets.s3cr3t";

    public static String getStaticString() {
      return staticString;
    }
  }

  public static class MySetHandler implements SecretContainerHandler {

    @SuppressWarnings({"ResultOfMethodCallIgnored", "unchecked"})
    @Override
    public void handleSecretContainer(Object input, SecretElementHandler elementHandler) {
      // plain secret Strings are directly replaced
      Set<Object> inputSet = (Set<Object>) input;
      inputSet.remove("secrets.s3cr3t");
      inputSet.add("plain");
      // all elements are handled afterward, to also handle potential containers
      inputSet.forEach(e -> elementHandler.handleSecretElement(e, "", "", Object::toString));
    }
  }
}
