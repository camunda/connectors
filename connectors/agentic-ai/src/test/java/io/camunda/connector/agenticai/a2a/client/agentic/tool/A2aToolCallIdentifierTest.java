/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.agentic.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class A2aToolCallIdentifierTest {

  @Nested
  class FullyQualifiedNameGeneration {

    @ParameterizedTest
    @MethodSource("fullyQualifiedNameCases")
    void generatesCorrectFullyQualifiedName(String elementName, String expectedFqn) {
      var identifier = new A2aToolCallIdentifier(elementName);

      var result = identifier.fullyQualifiedName();

      assertThat(result).isEqualTo(expectedFqn);
    }

    static Stream<Arguments> fullyQualifiedNameCases() {
      return Stream.of(
          arguments("myElement", "A2A_myElement"),
          arguments("my-element_123", "A2A_my-element_123"),
          arguments("Element.Name.v2", "A2A_Element.Name.v2"));
    }
  }

  @Nested
  class ToolCallIdentifierValidation {

    @ParameterizedTest
    @MethodSource("validA2AToolCallNames")
    void returnsTrueWhenValidA2aToolCallIdentifier(String toolCallName) {
      assertThat(A2aToolCallIdentifier.isA2aToolCallIdentifier(toolCallName)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("invalidA2AToolCallNames")
    void returnsFalseWhenInvalidA2aToolCallIdentifier(String toolCallName) {
      assertThat(A2aToolCallIdentifier.isA2aToolCallIdentifier(toolCallName)).isFalse();
    }

    static Stream<Arguments> validA2AToolCallNames() {
      return Stream.of(
          arguments("A2A_element"),
          arguments("A2A_my-element"),
          arguments("A2A_element123"),
          arguments("A2A_element_name"),
          arguments("A2A_a"),
          arguments("A2A_very-long-element-name"),
          arguments("A2A_CamelCase"),
          arguments("A2A_Element.Name.v2"));
    }

    static Stream<Arguments> invalidA2AToolCallNames() {
      return Stream.of(
          arguments("element"), // missing A2A prefix
          arguments("A2A_"), // missing element name
          arguments("A2A"), // incomplete, missing underscore and name
          arguments(""), // empty
          arguments("A2A element"), // space after prefix
          arguments("NotA2A_element")); // wrong prefix
    }
  }

  @Nested
  class ToolCallNameParsing {

    @ParameterizedTest
    @MethodSource("parseCases")
    void parsesCorrectlyWhenValidToolCallName(String toolCallName, String expectedElementName) {
      var result = A2aToolCallIdentifier.fromToolCallName(toolCallName);

      assertThat(result.elementName()).isEqualTo(expectedElementName);
    }

    static Stream<Arguments> parseCases() {
      return Stream.of(
          arguments("A2A_myElement", "myElement"),
          arguments("A2A_my-element_123", "my-element_123"),
          arguments("A2A_a", "a"),
          arguments("A2A_Element.Name.v2", "Element.Name.v2"));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "A2A_", // missing element name
          "element", // missing prefix
          "A2A" // incomplete
        })
    void throwsExceptionWhenInvalidToolCallName(String invalidToolCallName) {
      assertThatThrownBy(() -> A2aToolCallIdentifier.fromToolCallName(invalidToolCallName))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Invalid A2A tool call name: '%s'".formatted(invalidToolCallName));
    }
  }

  @Nested
  class RoundTripConsistency {

    @ParameterizedTest
    @MethodSource("roundTripScenarios")
    void maintainsConsistencyWhenGeneratingAndParsingNames(String elementName) {
      var original = new A2aToolCallIdentifier(elementName);
      var fullyQualifiedName = original.fullyQualifiedName();
      var parsed = A2aToolCallIdentifier.fromToolCallName(fullyQualifiedName);

      assertThat(parsed).isEqualTo(original);
      assertThat(parsed.elementName()).isEqualTo(elementName);
    }

    static Stream<Arguments> roundTripScenarios() {
      return Stream.of(
          arguments("element"),
          arguments("my-element"),
          arguments("element_123"),
          arguments("very-long-element-name"),
          arguments("a"),
          arguments("element-with-dashes"),
          arguments("element_with_underscores"),
          arguments("CamelCaseElement"),
          arguments("Element.Name.v2"));
    }
  }
}
