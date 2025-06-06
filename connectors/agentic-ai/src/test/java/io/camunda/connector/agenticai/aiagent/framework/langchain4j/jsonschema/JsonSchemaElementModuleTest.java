/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;

class JsonSchemaElementModuleTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JsonSchemaElementModule());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "jsonschema/mcp-server-everything-tools-list.json",
        "jsonschema/mcp-server-filesystem-tools-list.json"
      })
  void deserializesAndSerializesMcpToolSchemas(String path) throws Exception {
    final var inputJson =
        Files.readString(Path.of(getClass().getClassLoader().getResource(path).getFile()));

    final var tools = objectMapper.readValue(inputJson, TestMcpListToolsResponse.class);
    assertThat(tools).isNotNull();

    final var serialized = objectMapper.writeValueAsString(tools);
    assertThat(serialized).isNotNull();
  }

  private record TestMcpListToolsResponse(List<TestMcpToolDefinition> tools) {
    private record TestMcpToolDefinition(
        String name, String description, JsonSchemaElement inputSchema) {}
  }

  @Test
  void shouldHandleSimpleObjectSchema() throws Exception {
    String json =
        """
        {
          "type": "object",
          "properties": {
            "message": {
              "type": "string",
              "description": "Message to echo"
            }
          },
          "required": [
            "message"
          ],
          "additionalProperties": false
        }
        """;

    assertSchemaSerializationAndDeserialization(
        json,
        schema -> {
          assertThat(schema.properties()).containsOnlyKeys("message");
          assertThat(schema.properties().get("message"))
              .asInstanceOf(InstanceOfAssertFactories.type(JsonStringSchema.class))
              .satisfies(
                  stringSchema -> {
                    assertThat(stringSchema.description()).isEqualTo("Message to echo");
                  });
          assertThat(schema.required()).containsExactly("message");
          assertThat(schema.additionalProperties()).isFalse();
        });
  }

  @Test
  void shouldHandleComplexObjectSchema() throws Exception {
    String json =
        """
        {
          "type": "object",
          "properties": {
            "type": {
              "enum": [
                "edit",
                "overwrite"
              ],
              "description": "Type of edit"
            },
            "path": {
              "type": "string"
            },
            "edits": {
              "type": "array",
              "description": "The edits to apply",
              "items": {
                "type": "object",
                "properties": {
                  "oldText": {
                    "type": "string",
                    "description": "Text to search for - must match exactly"
                  },
                  "newText": {
                    "type": "string",
                    "description": "Text to replace with"
                  }
                },
                "required": [
                  "oldText",
                  "newText"
                ],
                "additionalProperties": true
              }
            },
            "maxLines": {
              "type": "number",
              "description": "The maximum number of lines to edit"
            },
            "startLine": {
              "type": "integer",
              "description": "Do not edit lines before this line number"
            },
            "dryRun": {
              "type": "boolean",
              "description": "Preview changes using git-style diff format"
            },
            "encoding": {
              "anyOf": [
                {
                  "type": "string",
                  "description": "Encoding string, e.g. 'utf-8', 'ascii', etc."
                },
                {
                  "type": "number",
                  "description": "Encoding codepage, e.g. 65001 for UTF-8"
                }
              ]
            }
          },
          "required": [
            "path",
            "edits"
          ],
          "additionalProperties": false
        }
        """;

    assertSchemaSerializationAndDeserialization(
        json,
        schema -> {
          assertThat(schema.properties())
              .containsOnlyKeys(
                  "type", "path", "edits", "maxLines", "startLine", "dryRun", "encoding");

          assertThat(schema.properties().get("type"))
              .asInstanceOf(InstanceOfAssertFactories.type(JsonEnumSchema.class))
              .satisfies(
                  enumSchema -> {
                    assertThat(enumSchema.enumValues()).contains("edit", "overwrite");
                    assertThat(enumSchema.description()).isEqualTo("Type of edit");
                  });

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
                    assertThat(arraySchema.description()).isEqualTo("The edits to apply");
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
                                            .isEqualTo("Text to search for - must match exactly");
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
                              assertThat(objectSchema.additionalProperties()).isTrue();
                            });
                  });

          assertThat(schema.properties().get("maxLines"))
              .asInstanceOf(InstanceOfAssertFactories.type(JsonNumberSchema.class))
              .satisfies(
                  numberSchema -> {
                    assertThat(numberSchema.description())
                        .isEqualTo("The maximum number of lines to edit");
                  });

          assertThat(schema.properties().get("startLine"))
              .asInstanceOf(InstanceOfAssertFactories.type(JsonIntegerSchema.class))
              .satisfies(
                  integerSchema -> {
                    assertThat(integerSchema.description())
                        .isEqualTo("Do not edit lines before this line number");
                  });

          assertThat(schema.properties().get("dryRun"))
              .asInstanceOf(InstanceOfAssertFactories.type(JsonBooleanSchema.class))
              .satisfies(
                  booleanSchema -> {
                    assertThat(booleanSchema.description())
                        .isEqualTo("Preview changes using git-style diff format");
                  });

          assertThat(schema.properties().get("encoding"))
              .asInstanceOf(InstanceOfAssertFactories.type(JsonAnyOfSchema.class))
              .satisfies(
                  anyOfSchema -> {
                    assertThat(anyOfSchema.anyOf()).hasSize(2);

                    assertThat(anyOfSchema.anyOf().get(0))
                        .asInstanceOf(InstanceOfAssertFactories.type(JsonStringSchema.class))
                        .satisfies(
                            stringSchema -> {
                              assertThat(stringSchema.description())
                                  .isEqualTo("Encoding string, e.g. 'utf-8', 'ascii', etc.");
                            });

                    assertThat(anyOfSchema.anyOf().get(1))
                        .asInstanceOf(InstanceOfAssertFactories.type(JsonNumberSchema.class))
                        .satisfies(
                            numberSchema -> {
                              assertThat(numberSchema.description())
                                  .isEqualTo("Encoding codepage, e.g. 65001 for UTF-8");
                            });
                  });

          assertThat(schema.required()).containsExactly("path", "edits");
          assertThat(schema.additionalProperties()).isFalse();
        });
  }

  @Test
  void shouldHandleSchemaIncludingEnum() throws Exception {
    String json =
        """
        {
          "type": "object",
          "properties": {
            "messageType": {
              "enum": [
                "error",
                "success",
                "debug"
              ],
              "description": "Type of message to demonstrate different annotation patterns"
            },
            "includeImage": {
              "type": "boolean",
              "description": "Whether to include an example image"
            }
          },
          "required": [
            "messageType"
          ],
          "additionalProperties": false
        }
        """;

    assertSchemaSerializationAndDeserialization(
        json,
        schema -> {
          assertThat(schema.properties()).containsKeys("messageType", "includeImage");
          assertThat(schema.properties().get("messageType"))
              .asInstanceOf(InstanceOfAssertFactories.type(JsonEnumSchema.class))
              .satisfies(
                  enumSchema -> {
                    assertThat(enumSchema.enumValues()).contains("error", "success", "debug");
                    assertThat(enumSchema.description())
                        .isEqualTo("Type of message to demonstrate different annotation patterns");
                  });
          assertThat(schema.properties().get("includeImage"))
              .asInstanceOf(InstanceOfAssertFactories.type(JsonBooleanSchema.class))
              .satisfies(
                  booleanSchema -> {
                    assertThat(booleanSchema.description())
                        .isEqualTo("Whether to include an example image");
                  });
          assertThat(schema.required()).containsExactly("messageType");
          assertThat(schema.additionalProperties()).isFalse();
        });
  }

  @Test
  void shouldHandleSchemaIncludingArray() throws Exception {
    String json =
        """
        {
          "type": "object",
          "properties": {
            "myItems": {
              "type": "array",
              "description": "Array description",
              "items": {
                "type": "string",
                "description": "Item description"
              }
            }
          },
          "required": [
            "myItems"
          ]
        }
        """;

    assertSchemaSerializationAndDeserialization(
        json,
        schema -> {
          assertThat(schema.properties()).containsKey("myItems");
          assertThat(schema.properties().get("myItems"))
              .asInstanceOf(InstanceOfAssertFactories.type(JsonArraySchema.class))
              .satisfies(
                  arraySchema -> {
                    assertThat(arraySchema.description()).isEqualTo("Array description");
                    assertThat(arraySchema.items())
                        .asInstanceOf(InstanceOfAssertFactories.type(JsonStringSchema.class))
                        .satisfies(
                            stringSchema -> {
                              assertThat(stringSchema.description()).isEqualTo("Item description");
                            });
                  });
          assertThat(schema.required()).containsExactly("myItems");
          assertThat(schema.additionalProperties()).isNull();
        });
  }

  @Test
  void shouldHandleSchemaWithReferences() throws Exception {
    String json =
        """
        {
          "type": "object",
          "properties": {
            "user": {
              "$ref": "#/$defs/User"
            }
          },
          "required": [
            "user"
          ],
          "additionalProperties": false,
          "$defs": {
            "User": {
              "type": "object",
              "description": "User object",
              "properties": {
                "name": {
                  "type": "string",
                  "description": "User's name"
                },
                "age": {
                  "type": "integer",
                  "description": "User's age"
                },
                "friends": {
                  "type": "array",
                  "description": "List of user's friends",
                  "items": {
                    "$ref": "#/$defs/User"
                  }
                }
              },
              "required": [
                "name",
                "age"
              ]
            }
          }
        }
        """;

    assertSchemaSerializationAndDeserialization(
        json,
        schema -> {
          assertThat(schema.properties()).containsKey("user");
          assertThat(schema.properties().get("user"))
              .asInstanceOf(InstanceOfAssertFactories.type(JsonReferenceSchema.class))
              .satisfies(
                  referenceSchema -> {
                    assertThat(referenceSchema.reference()).isEqualTo("#/$defs/User");
                  });

          assertThat(schema.required()).containsExactly("user");
          assertThat(schema.additionalProperties()).isFalse();

          assertThat(schema.definitions()).containsKey("User");
          assertThat(schema.definitions().get("User"))
              .asInstanceOf(InstanceOfAssertFactories.type(JsonObjectSchema.class))
              .satisfies(
                  userSchema -> {
                    assertThat(userSchema.description()).isEqualTo("User object");
                    assertThat(userSchema.properties()).containsOnlyKeys("name", "age", "friends");

                    assertThat(userSchema.properties().get("name"))
                        .asInstanceOf(InstanceOfAssertFactories.type(JsonStringSchema.class))
                        .satisfies(
                            stringSchema -> {
                              assertThat(stringSchema.description()).isEqualTo("User's name");
                            });

                    assertThat(userSchema.properties().get("age"))
                        .asInstanceOf(InstanceOfAssertFactories.type(JsonIntegerSchema.class))
                        .satisfies(
                            integerSchema -> {
                              assertThat(integerSchema.description()).isEqualTo("User's age");
                            });

                    assertThat(userSchema.properties().get("friends"))
                        .asInstanceOf(InstanceOfAssertFactories.type(JsonArraySchema.class))
                        .satisfies(
                            arraySchema -> {
                              assertThat(arraySchema.description())
                                  .isEqualTo("List of user's friends");
                              assertThat(arraySchema.items())
                                  .asInstanceOf(
                                      InstanceOfAssertFactories.type(JsonReferenceSchema.class))
                                  .satisfies(
                                      itemReference ->
                                          assertThat(itemReference.reference())
                                              .isEqualTo("#/$defs/User"));
                            });

                    assertThat(userSchema.required()).containsExactly("name", "age");
                  });
        });
  }

  @Test
  void shouldIgnoreUnknownProperties() throws Exception {
    String json =
        """
        {
          "type": "object",
          "properties": {
            "message": {
              "type": "string",
              "description": "Message to echo",
              "unknownProperty": "value"
            }
          },
          "required": [
            "message"
          ],
          "additionalProperties": false,
          "anotherUnknownProperty": 123
        }
        """;

    assertSchemaSerializationAndDeserialization(
        json,
        schema -> {
          assertThat(schema.properties()).containsKey("message");
          assertThat(schema.properties().get("message"))
              .asInstanceOf(InstanceOfAssertFactories.type(JsonStringSchema.class))
              .satisfies(
                  stringSchema -> {
                    assertThat(stringSchema.description()).isEqualTo("Message to echo");
                  });
          assertThat(schema.required()).contains("message");
          assertThat(schema.additionalProperties()).isFalse();
        },
        false);
  }

  @Test
  void throwsExceptionWhenDeserializingUnknownType() {
    assertThatThrownBy(
            () -> objectMapper.readValue("{\"type\": \"dummy\"}", JsonSchemaElement.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Unknown JSON schema element type 'dummy'");
  }

  @Test
  void throwsExceptionWhenSerializingUnknownType() {
    assertThatThrownBy(() -> objectMapper.writeValueAsString(new JsonTestSchema()))
        .isInstanceOf(JsonMappingException.class)
        .hasMessage(
            "Unsupported JSON schema type '%s'".formatted(JsonTestSchema.class.getSimpleName()));
  }

  private void assertSchemaSerializationAndDeserialization(
      String inputJson, ThrowingConsumer<JsonObjectSchema> schemaAssertions) throws Exception {
    assertSchemaSerializationAndDeserialization(inputJson, schemaAssertions, true);
  }

  private void assertSchemaSerializationAndDeserialization(
      String inputJson, ThrowingConsumer<JsonObjectSchema> schemaAssertions, boolean compareJson)
      throws Exception {
    // deserialize JSON
    JsonSchemaElement element = objectMapper.readValue(inputJson, JsonSchemaElement.class);
    assertThat(element)
        .asInstanceOf(InstanceOfAssertFactories.type(JsonObjectSchema.class))
        .satisfies(schemaAssertions);

    // compare serialized JSON with original input
    String serialized = objectMapper.writeValueAsString(element);
    if (compareJson) {
      JSONAssert.assertEquals(inputJson, serialized, true);
    }

    // deserialize back from serialized JSON
    JsonSchemaElement deserialized = objectMapper.readValue(serialized, JsonSchemaElement.class);
    assertThat(deserialized)
        .asInstanceOf(InstanceOfAssertFactories.type(JsonObjectSchema.class))
        .satisfies(schemaAssertions);
  }

  private static class JsonTestSchema implements JsonSchemaElement {
    @Override
    public String description() {
      return "test";
    }
  }
}
