/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.agenticai.aiagent.localtool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.DocumentFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LocalToolBuilderTest {

  private static LocalToolContext context() {
    return new LocalToolContext(
        null,
        Map.of(),
        DocumentRegistry.empty(),
        Set.of(),
        null,
        TestObjectMapperSupplier.INSTANCE,
        mock(DocumentFactory.class));
  }

  @Test
  void buildsTheDefinitionFromNameDescriptionAndSchema() {
    final var tool =
        LocalTool.builder("lookup")
            .description("Looks something up.")
            .inputSchema(
                LocalToolSchema.object()
                    .string("query", "What to look up", true)
                    .document("file", "The file", false))
            .handler((call, context) -> LocalToolResult.success("ok"));

    assertThat(tool.definition().name()).isEqualTo("lookup");
    assertThat(tool.definition().description()).isEqualTo("Looks something up.");
    assertThat(tool.definition().inputSchema())
        .containsEntry("type", "object")
        .containsEntry("required", List.of("query"));
    @SuppressWarnings("unchecked")
    final var properties = (Map<String, Object>) tool.definition().inputSchema().get("properties");
    assertThat(properties).containsOnlyKeys("query", "file");
    assertThat(properties.get("file"))
        .isEqualTo(
            Map.of(
                "type",
                "string",
                "format",
                "camunda-document-id",
                "description",
                "The file. " + DocumentParameterSchema.HINT));
  }

  @Test
  void keepsTheSchemaInDeclarationOrder() {
    final var schema =
        LocalToolSchema.object()
            .string("zeta", "z", true)
            .integer("alpha", "a", false)
            .stringArray("mid", "m", true)
            .build();

    assertThat(schema.keySet()).containsExactly("type", "properties", "required");
    @SuppressWarnings("unchecked")
    final var properties = (Map<String, Object>) schema.get("properties");
    assertThat(properties.keySet()).containsExactly("zeta", "alpha", "mid");
    @SuppressWarnings("unchecked")
    final var alpha = (Map<String, Object>) properties.get("alpha");
    assertThat(alpha.keySet()).containsExactly("type", "description");
    @SuppressWarnings("unchecked")
    final var mid = (Map<String, Object>) properties.get("mid");
    assertThat(mid.keySet()).containsExactly("type", "description", "items");
  }

  @Test
  void keepsTheOrderOfAMapSchemaInTheDefinition() {
    final var schema = new java.util.LinkedHashMap<String, Object>();
    schema.put("type", "object");
    schema.put("properties", Map.of());
    schema.put("required", List.of());

    final var tool = LocalTool.builder("t").inputSchema(schema).handler((call, context) -> null);

    assertThat(tool.definition().inputSchema().keySet())
        .containsExactly("type", "properties", "required");
  }

  @Test
  void theReturnDocumentDefinitionKeepsADeterministicKeyOrder() {
    final var schema = new ReturnDocumentTool().definition().inputSchema();

    assertThat(schema.keySet()).containsExactly("type", "properties", "required");
    @SuppressWarnings("unchecked")
    final var properties = (Map<String, Object>) schema.get("properties");
    assertThat(properties.keySet()).containsExactly("key", "document", "description");
    assertThat(((Map<String, Object>) properties.get("key")).keySet())
        .containsExactly("type", "description");
    assertThat(((Map<String, Object>) properties.get("document")).keySet())
        .containsExactly("type", "format", "description");
  }

  @Test
  void bindsTheArgumentsToTheTypeOfTheHandler() {
    record Args(String query, int limit) {}
    final var tool =
        LocalTool.builder("lookup")
            .handler(
                Args.class,
                (args, context) -> LocalToolResult.success(args.query() + ":" + args.limit()));

    final var result =
        tool.call(new LocalToolCall("1", "lookup", Map.of("query", "a", "limit", 3)), context());

    assertThat(result.content()).isEqualTo("a:3");
  }

  @Test
  void anErrorResultCarriesNoContextUpdate() {
    final var result = LocalToolResult.error("no").withContextUpdate(agentContext -> agentContext);

    assertThat(result.error()).isTrue();
    assertThat(result.contextUpdates()).isEmpty();
  }

  @Test
  void usesAReadyMadeDefinitionAsIs() {
    final var definition = new ReturnDocumentTool().definition();

    final var tool = LocalTool.builder(definition).handler((call, context) -> null);

    assertThat(tool.definition()).isSameAs(definition);
  }
}
