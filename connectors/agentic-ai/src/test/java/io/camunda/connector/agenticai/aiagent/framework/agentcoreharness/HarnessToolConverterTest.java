/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.agentcoreharness;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import software.amazon.awssdk.services.bedrockagentcore.model.HarnessToolType;

class HarnessToolConverterTest {

  private final HarnessToolConverter converter = new HarnessToolConverter();

  @ParameterizedTest
  @NullAndEmptySource
  void toHarnessToolsReturnsEmptyListForNullOrEmpty(List<ToolDefinition> toolDefinitions) {
    assertThat(converter.toHarnessTools(toolDefinitions)).isEmpty();
  }

  @Test
  void toHarnessToolConvertsBasicToolDefinition() {
    var toolDefinition =
        ToolDefinition.builder()
            .name("get_weather")
            .description("Get the current weather for a location")
            .inputSchema(
                Map.of(
                    "type", "object",
                    "properties", Map.of("location", Map.of("type", "string")),
                    "required", List.of("location")))
            .build();

    var result = converter.toHarnessTool(toolDefinition);

    assertThat(result.name()).isEqualTo("get_weather");
    assertThat(result.type()).isEqualTo(HarnessToolType.INLINE_FUNCTION);
    assertThat(result.config().inlineFunction()).isNotNull();
    assertThat(result.config().inlineFunction().description())
        .isEqualTo("Get the current weather for a location");
  }

  @Test
  void toHarnessToolsConvertsMultipleTools() {
    var tool1 =
        ToolDefinition.builder()
            .name("tool_one")
            .description("First tool")
            .inputSchema(Map.of("type", "object"))
            .build();
    var tool2 =
        ToolDefinition.builder()
            .name("tool_two")
            .description("Second tool")
            .inputSchema(Map.of("type", "object"))
            .build();

    var result = converter.toHarnessTools(List.of(tool1, tool2));

    assertThat(result).hasSize(2);
    assertThat(result.get(0).name()).isEqualTo("tool_one");
    assertThat(result.get(1).name()).isEqualTo("tool_two");
  }

  @Test
  void toHarnessToolHandlesComplexInputSchema() {
    var toolDefinition =
        ToolDefinition.builder()
            .name("complex_tool")
            .description("A tool with complex schema")
            .inputSchema(
                Map.of(
                    "type", "object",
                    "properties",
                        Map.of(
                            "name", Map.of("type", "string"),
                            "count", Map.of("type", "number"),
                            "enabled", Map.of("type", "boolean"),
                            "tags", Map.of("type", "array", "items", Map.of("type", "string"))),
                    "required", List.of("name")))
            .build();

    var result = converter.toHarnessTool(toolDefinition);

    assertThat(result.name()).isEqualTo("complex_tool");
    assertThat(result.config().inlineFunction().inputSchema()).isNotNull();
    var schema = result.config().inlineFunction().inputSchema();
    assertThat(schema.asMap()).containsKey("type");
    assertThat(schema.asMap().get("type").asString()).isEqualTo("object");
  }

  @Test
  void toHarnessToolHandlesEmptyInputSchema() {
    var toolDefinition =
        ToolDefinition.builder()
            .name("no_params_tool")
            .description("A tool with no parameters")
            .inputSchema(Map.of())
            .build();

    var result = converter.toHarnessTool(toolDefinition);

    assertThat(result.name()).isEqualTo("no_params_tool");
    assertThat(result.config().inlineFunction().inputSchema().asMap()).isEmpty();
  }

  @Test
  void toHarnessToolHandlesNullInputSchema() {
    var toolDefinition =
        ToolDefinition.builder()
            .name("null_schema_tool")
            .description("A tool with null schema")
            .inputSchema(null)
            .build();

    var result = converter.toHarnessTool(toolDefinition);

    assertThat(result.name()).isEqualTo("null_schema_tool");
    assertThat(result.config().inlineFunction().inputSchema().asMap()).isEmpty();
  }
}
