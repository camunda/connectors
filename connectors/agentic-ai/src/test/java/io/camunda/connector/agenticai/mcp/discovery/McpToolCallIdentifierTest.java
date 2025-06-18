/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.discovery;

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

class McpToolCallIdentifierTest {

  @Nested
  class FullyQualifiedNameGeneration {

    @Test
    void generatesCorrectName_whenValidElementAndToolName() {
      var identifier = new McpToolCallIdentifier("myElement", "myTool");

      var result = identifier.fullyQualifiedName();

      assertThat(result).isEqualTo("MCP_myElement___myTool");
    }

    @Test
    void generatesCorrectName_whenElementNameContainsSpecialCharacters() {
      var identifier = new McpToolCallIdentifier("my-element_123", "file-read");

      var result = identifier.fullyQualifiedName();

      assertThat(result).isEqualTo("MCP_my-element_123___file-read");
    }

    @Test
    void generatesCorrectName_whenToolNameContainsSpecialCharacters() {
      var identifier = new McpToolCallIdentifier("element", "database_query-user");

      var result = identifier.fullyQualifiedName();

      assertThat(result).isEqualTo("MCP_element___database_query-user");
    }

    @Test
    void generatesCorrectName_whenBothNamesContainSpecialCharacters() {
      var identifier = new McpToolCallIdentifier("my_element-123", "my_tool_action-v2");

      var result = identifier.fullyQualifiedName();

      assertThat(result).isEqualTo("MCP_my_element-123___my_tool_action-v2");
    }
  }

  @Nested
  class ToolCallIdentifierValidation {

    @ParameterizedTest
    @MethodSource("validMcpToolCallNames")
    void returnsTrue_whenValidMcpToolCallIdentifier(String toolCallName) {
      assertThat(McpToolCallIdentifier.isMcpToolCallIdentifier(toolCallName)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("invalidMcpToolCallNames")
    void returnsFalse_whenInvalidMcpToolCallIdentifier(String toolCallName) {
      assertThat(McpToolCallIdentifier.isMcpToolCallIdentifier(toolCallName)).isFalse();
    }

    static Stream<Arguments> validMcpToolCallNames() {
      return Stream.of(
          arguments("MCP_element___tool"),
          arguments("MCP_my-element___my-tool"),
          arguments("MCP_element123___tool_action"),
          arguments("MCP_element_name___tool_name"),
          arguments("MCP_a___b"),
          arguments("MCP_very-long-element-name___very-long-tool-name"));
    }

    static Stream<Arguments> invalidMcpToolCallNames() {
      return Stream.of(
          arguments("element___tool"), // missing MCP prefix
          arguments("MCP_element_tool"), // missing separator
          arguments("MCP_element___"), // missing tool name
          arguments("MCP____tool"), // missing element name
          arguments("MCP___"), // missing both names
          arguments("MCP_"), // incomplete
          arguments("regular-tool-name"), // not MCP format
          arguments(""), // empty
          arguments("MCP"), // just prefix
          arguments("NotMCP_element___tool")); // wrong prefix
    }
  }

  @Nested
  class ToolCallNameParsing {

    @Test
    void parsesCorrectly_whenValidToolCallName() {
      var result = McpToolCallIdentifier.fromToolCallName("MCP_myElement___myTool");

      assertThat(result.elementName()).isEqualTo("myElement");
      assertThat(result.mcpToolName()).isEqualTo("myTool");
    }

    @Test
    void parsesCorrectly_whenNamesContainSpecialCharacters() {
      var result = McpToolCallIdentifier.fromToolCallName("MCP_my-element_123___file_read-action");

      assertThat(result.elementName()).isEqualTo("my-element_123");
      assertThat(result.mcpToolName()).isEqualTo("file_read-action");
    }

    @Test
    void parsesCorrectly_whenMinimalValidNames() {
      var result = McpToolCallIdentifier.fromToolCallName("MCP_a___b");

      assertThat(result.elementName()).isEqualTo("a");
      assertThat(result.mcpToolName()).isEqualTo("b");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "MCP_element___", // missing tool name
          "MCP____tool", // missing element name
          "MCP___", // missing both
          "element___tool", // missing prefix
          "MCP_element_tool", // missing separator
          "MCP_element___tool___extra" // too many parts
        })
    void throwsException_whenInvalidToolCallName(String invalidToolCallName) {
      assertThatThrownBy(() -> McpToolCallIdentifier.fromToolCallName(invalidToolCallName))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(
              "Failed to parse MCP tool call identifier from '%s'".formatted(invalidToolCallName));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    void throwsException_whenElementNameIsBlank(String blankElementName) {
      var toolCallName = "MCP_" + blankElementName + "___tool";

      assertThatThrownBy(() -> McpToolCallIdentifier.fromToolCallName(toolCallName))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Failed to parse MCP tool call identifier from '%s'".formatted(toolCallName));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    void throwsException_whenToolNameIsBlank(String blankToolName) {
      var toolCallName = "MCP_element___" + blankToolName;

      assertThatThrownBy(() -> McpToolCallIdentifier.fromToolCallName(toolCallName))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Failed to parse MCP tool call identifier from '%s'".formatted(toolCallName));
    }
  }

  @Nested
  class RoundTripConsistency {

    @ParameterizedTest
    @MethodSource("roundTripScenarios")
    void maintainsConsistency_whenGeneratingAndParsingNames(String elementName, String toolName) {
      var original = new McpToolCallIdentifier(elementName, toolName);
      var fullyQualifiedName = original.fullyQualifiedName();
      var parsed = McpToolCallIdentifier.fromToolCallName(fullyQualifiedName);

      assertThat(parsed).isEqualTo(original);
      assertThat(parsed.elementName()).isEqualTo(elementName);
      assertThat(parsed.mcpToolName()).isEqualTo(toolName);
    }

    static Stream<Arguments> roundTripScenarios() {
      return Stream.of(
          arguments("element", "tool"),
          arguments("my-element", "my-tool"),
          arguments("element_123", "tool_action"),
          arguments("very-long-element-name", "very-long-tool-name"),
          arguments("a", "b"),
          arguments("element-with-dashes", "tool-with-dashes"),
          arguments("element_with_underscores", "tool_with_underscores"),
          arguments("CamelCaseElement", "CamelCaseTool"));
    }
  }
}
