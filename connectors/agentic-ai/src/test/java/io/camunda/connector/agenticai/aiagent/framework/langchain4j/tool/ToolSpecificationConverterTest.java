/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

class ToolSpecificationConverterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ToolSpecificationConverter toolSpecificationConverter =
      new ToolSpecificationConverterImpl(objectMapper);

  @Test
  void convertsToolDefinitionToToolSpecification() throws Exception {
    final var toolsDefinitions =
        loadToolDefinitions("jsonschema/mcp-server-filesystem-tools-list.json");
    final var toolDefinition =
        toolsDefinitions.tools().stream()
            .filter(td -> td.name().equals("edit_file"))
            .findFirst()
            .get();

    final var toolSpecification = toolSpecificationConverter.asToolSpecification(toolDefinition);

    assertThat(toolSpecification.name()).isEqualTo("edit_file");
    assertThat(toolSpecification.description())
        .isEqualTo(
            "Make line-based edits to a text file. Each edit replaces exact line sequences with new content. Returns a git-style diff showing the changes made. Only works within allowed directories.");
    assertThat(toolSpecification.parameters())
        .satisfies(
            schema -> {
              assertThat(schema.properties()).containsOnlyKeys("path", "edits", "dryRun");

              assertThat(schema.properties().get("path"))
                  .asInstanceOf(InstanceOfAssertFactories.type(JsonStringSchema.class))
                  .satisfies(
                      stringSchema -> {
                        assertThat(stringSchema.description()).isNull();
                      });

              assertThat(schema.properties().get("edits"))
                  .asInstanceOf(InstanceOfAssertFactories.type(JsonArraySchema.class))
                  .satisfies(
                      arraySchema -> {
                        assertThat(arraySchema.items())
                            .asInstanceOf(InstanceOfAssertFactories.type(JsonObjectSchema.class))
                            .satisfies(
                                objectSchema -> {
                                  assertThat(objectSchema.properties())
                                      .containsOnlyKeys("oldText", "newText");
                                  assertThat(objectSchema.properties().get("oldText"))
                                      .asInstanceOf(
                                          InstanceOfAssertFactories.type(JsonStringSchema.class))
                                      .satisfies(
                                          stringSchema -> {
                                            assertThat(stringSchema.description())
                                                .isEqualTo(
                                                    "Text to search for - must match exactly");
                                          });
                                  assertThat(objectSchema.properties().get("newText"))
                                      .asInstanceOf(
                                          InstanceOfAssertFactories.type(JsonStringSchema.class))
                                      .satisfies(
                                          stringSchema -> {
                                            assertThat(stringSchema.description())
                                                .isEqualTo("Text to replace with");
                                          });
                                  assertThat(objectSchema.required())
                                      .containsExactly("oldText", "newText");
                                  assertThat(objectSchema.additionalProperties()).isFalse();
                                });
                      });

              assertThat(schema.properties().get("dryRun"))
                  .asInstanceOf(InstanceOfAssertFactories.type(JsonBooleanSchema.class))
                  .satisfies(
                      booleanSchema -> {
                        assertThat(booleanSchema.description())
                            .isEqualTo("Preview changes using git-style diff format");
                      });

              assertThat(schema.required()).containsExactly("path", "edits");
              assertThat(schema.additionalProperties()).isFalse();
            });
  }

  @Test
  void convertsToolSpecificationToToolDefinition() throws Exception {
    final var toolSpecification =
        ToolSpecification.builder()
            .name("test_tool")
            .description("A test tool for validation")
            .parameters(
                JsonObjectSchema.builder()
                    .addStringProperty("name", "Your name")
                    .addNumberProperty("age", "Your age")
                    .addEnumProperty(
                        "eatingPreferences",
                        List.of("vegetarian", "vegan", "anything"),
                        "Your eating preferences")
                    .required("name", "age")
                    .build())
            .build();

    final var toolDefinition = toolSpecificationConverter.asToolDefinition(toolSpecification);

    assertThat(toolDefinition.name()).isEqualTo("test_tool");
    assertThat(toolDefinition.description()).isEqualTo("A test tool for validation");
    assertThat(toolDefinition.inputSchema().get("type")).isEqualTo("object");
    assertThat(toolDefinition.inputSchema().get("required"))
        .asInstanceOf(InstanceOfAssertFactories.list(String.class))
        .containsExactly("name", "age");

    final String expectedSchemaJson =
        """
        {
          "type": "object",
          "properties": {
            "name": {
              "type": "string",
              "description": "Your name"
            },
            "age": {
              "type": "number",
              "description": "Your age"
            },
            "eatingPreferences": {
              "description": "Your eating preferences",
              "enum": [
                "vegetarian",
                "vegan",
                "anything"
              ]
            }
          },
          "required": [
            "name",
            "age"
          ]
        }
        """;

    JSONAssert.assertEquals(
        expectedSchemaJson, objectMapper.writeValueAsString(toolDefinition.inputSchema()), true);
  }

  private TestToolDefinitions loadToolDefinitions(String path) throws IOException {
    final var inputJson =
        Files.readString(Path.of(getClass().getClassLoader().getResource(path).getFile()));

    return objectMapper.readValue(inputJson, TestToolDefinitions.class);
  }

  private record TestToolDefinitions(List<ToolDefinition> tools) {}
}
