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

import static org.junit.jupiter.api.Assertions.*;

import io.camunda.connector.test.ConnectorContextBuilder;
import io.camunda.connector.test.ConnectorContextBuilder.TestConnectorContext;
import java.lang.reflect.Field;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.ThrowingConsumer;

public class ConnectorContextTest {
  private static Field fieldForName(Class<?> clazz, String fieldName) {
    try {
      return clazz.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  @TestFactory
  public Stream<DynamicTest> shouldGetPropertyFromField() {
    // given
    TestConnectorContext connectorContext = ConnectorContextBuilder.create().build();
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
              connectorContext.getProperty(testInput, fieldForName(TestInput.class, in.fieldName));
          // then
          assertEquals(in.expectedContent, content);
        };
    return DynamicTest.stream(input, i -> i.fieldName, executor);
  }

  @TestFactory
  public Stream<DynamicTest> shouldSetPropertyToField() {
    // given
    TestConnectorContext connectorContext = ConnectorContextBuilder.create().build();
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
          connectorContext.setProperty(
              testInput, fieldForName(TestInput.class, in.fieldName), modified);
          // then
          assertEquals(modified, in.getter.get());
        };
    return DynamicTest.stream(input, i -> i.fieldName, executor);
  }

  @Test
  public void shouldThrowWhenSettingOnFinalField() {
    // given
    TestConnectorContext connectorContext = ConnectorContextBuilder.create().build();
    TestInput testInput = new TestInput();
    String modified = "modified";
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                // when
                connectorContext.setProperty(
                    testInput, fieldForName(TestInput.class, "finalField"), modified));
    // then
    assertEquals(
        "Cannot invoke set or setter on final field 'finalField' of type class io.camunda.connector.impl.TestInput",
        exception.getMessage());
  }

  @Test
  public void shouldReplaceSecretsAlongAnnotations() {
    // given
    TestConnectorContext connectorContext =
        ConnectorContextBuilder.create().secret("s3cr3t", "s3cr3t").build();
    ComplexTestInput testInput =
        new ComplexTestInput(new ComplexTestInput(new ComplexTestInput(null)));
    // when
    connectorContext.replaceSecrets(testInput);
    // then
    ComplexTestInput testedInput = testInput;
    while (testedInput != null) {
      for (TestInput input : testInput.inputArray) {
        assertEquals("s3cr3t", input.getSecretField());
      }
      testInput.testInputs.forEach(input -> assertEquals("s3cr3t", input.getSecretField()));
      assertEquals("s3cr3t", testInput.secretContainer.getSecretField());
      // it does not replace secrets in nested properties that are not marked
      assertEquals("secrets.s3cr3t", testInput.otherProperty.getSecretField());
      testedInput = testedInput.complexInput;
    }
  }

  private static class SetPropertyTestInput {
    private String fieldName;
    private Supplier<String> getter;

    public SetPropertyTestInput(String fieldName, Supplier<String> getter) {
      this.fieldName = fieldName;
      this.getter = getter;
    }
  }

  private static class GetPropertyTestInput {
    private String fieldName;
    private String expectedContent;

    public GetPropertyTestInput(String fieldName, String expectedContent) {
      this.fieldName = fieldName;
      this.expectedContent = expectedContent;
    }
  }
}
