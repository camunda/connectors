/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.localtoolbox.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class LocalToolboxToolCallIdentifierTest {

  @Nested
  class FullyQualifiedNameGeneration {

    @Test
    void generatesCorrectName_whenValidElementAndToolName() {
      var identifier = new LocalToolboxToolCallIdentifier("myElement", "myTool");

      var result = identifier.fullyQualifiedName();

      assertThat(result).isEqualTo("LOCALTOOLBOX_myElement___myTool");
    }

    @Test
    void generatesCorrectName_whenNamesContainSpecialCharacters() {
      var identifier = new LocalToolboxToolCallIdentifier("my-element_123", "file-read");

      var result = identifier.fullyQualifiedName();

      assertThat(result).isEqualTo("LOCALTOOLBOX_my-element_123___file-read");
    }
  }

  @Nested
  class ToolCallIdentifierValidation {

    @ParameterizedTest
    @MethodSource("validToolCallNames")
    void returnsTrue_whenValidLocalToolboxToolCallIdentifier(String toolCallName) {
      assertThat(LocalToolboxToolCallIdentifier.isLocalToolboxToolCallIdentifier(toolCallName))
          .isTrue();
    }

    @ParameterizedTest
    @MethodSource("invalidToolCallNames")
    void returnsFalse_whenInvalidLocalToolboxToolCallIdentifier(String toolCallName) {
      assertThat(LocalToolboxToolCallIdentifier.isLocalToolboxToolCallIdentifier(toolCallName))
          .isFalse();
    }

    static Stream<Arguments> validToolCallNames() {
      return Stream.of(
          arguments("LOCALTOOLBOX_element___tool"),
          arguments("LOCALTOOLBOX_my-element___my-tool"),
          arguments("LOCALTOOLBOX_a___b"),
          arguments("LOCALTOOLBOX_element___tool___with___separators"));
    }

    static Stream<Arguments> invalidToolCallNames() {
      return Stream.of(
          arguments("element___tool"), // missing prefix
          arguments("LOCALTOOLBOX_element_tool"), // missing separator
          arguments("LOCALTOOLBOX_element___"), // missing tool name
          arguments("LOCALTOOLBOX____tool"), // missing element name
          arguments("LOCALTOOLBOX___"), // missing both
          arguments(""), // empty
          arguments("MCP_element___tool")); // wrong prefix
    }
  }

  @Nested
  class ToolCallNameParsing {

    @Test
    void parsesCorrectly_whenValidToolCallName() {
      var result =
          LocalToolboxToolCallIdentifier.fromToolCallName("LOCALTOOLBOX_myElement___myTool");

      assertThat(result.elementId()).isEqualTo("myElement");
      assertThat(result.toolName()).isEqualTo("myTool");
    }

    @Test
    void parsesCorrectly_whenToolNameHasSeparator() {
      var result =
          LocalToolboxToolCallIdentifier.fromToolCallName(
              "LOCALTOOLBOX_Activity_1mlgkr7___loan-affordability___loan-affordability");

      assertThat(result.elementId()).isEqualTo("Activity_1mlgkr7");
      assertThat(result.toolName()).isEqualTo("loan-affordability___loan-affordability");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "LOCALTOOLBOX_element___",
          "LOCALTOOLBOX____tool",
          "LOCALTOOLBOX___",
          "element___tool",
          "LOCALTOOLBOX_element_tool"
        })
    void throwsException_whenInvalidToolCallName(String invalidToolCallName) {
      assertThatThrownBy(() -> LocalToolboxToolCallIdentifier.fromToolCallName(invalidToolCallName))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(
              "Failed to parse local toolbox tool call identifier from '%s'"
                  .formatted(invalidToolCallName));
    }
  }

  @Nested
  class RoundTripConsistency {

    @ParameterizedTest
    @MethodSource("roundTripScenarios")
    void maintainsConsistency_whenGeneratingAndParsingNames(String elementName, String toolName) {
      var original = new LocalToolboxToolCallIdentifier(elementName, toolName);
      var fullyQualifiedName = original.fullyQualifiedName();
      var parsed = LocalToolboxToolCallIdentifier.fromToolCallName(fullyQualifiedName);

      assertThat(parsed).isEqualTo(original);
      assertThat(parsed.elementId()).isEqualTo(elementName);
      assertThat(parsed.toolName()).isEqualTo(toolName);
    }

    static Stream<Arguments> roundTripScenarios() {
      return Stream.of(
          arguments("element", "tool"),
          arguments("my-element", "my-tool"),
          arguments("element_123", "tool_action"),
          arguments("Activity_1mlgkr7", "loan-affordability___loan-affordability"));
    }
  }
}
