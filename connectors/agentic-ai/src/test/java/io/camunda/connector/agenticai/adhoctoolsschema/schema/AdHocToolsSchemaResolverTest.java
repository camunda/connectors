/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdHocToolsSchemaResolverTest {

  private static final Map<String, Object> DUMMY_SCHEMA = Map.of("type", "dummy");

  @Mock private AdHocToolSchemaGenerator schemaGenerator;
  private AdHocToolsSchemaResolverImpl resolver;

  @BeforeEach
  void setUp() {
    resolver = new AdHocToolsSchemaResolverImpl(List.of(), schemaGenerator);
  }

  @Test
  void createsToolDefinitionFromElementWithDocumentation() {
    final var element =
        AdHocToolElement.builder()
            .elementId("Test_Tool")
            .elementName("Test Tool")
            .documentation("This is a test tool.")
            .build();

    when(schemaGenerator.generateToolSchema(element)).thenReturn(DUMMY_SCHEMA);

    final var schemaResponse = resolver.resolveAdHocToolsSchema(List.of(element));
    assertThat(schemaResponse.toolDefinitions())
        .singleElement()
        .satisfies(
            toolDefinition -> {
              assertThat(toolDefinition.name()).isEqualTo("Test_Tool");
              assertThat(toolDefinition.description()).isEqualTo("This is a test tool.");
              assertThat(toolDefinition.inputSchema()).isEqualTo(DUMMY_SCHEMA);
            });
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void createsToolDefinitionFromElementWithMissingDocumentation(String documentation) {
    final var element =
        AdHocToolElement.builder()
            .elementId("Test_Tool")
            .elementName("Test Tool")
            .documentation(documentation)
            .build();

    when(schemaGenerator.generateToolSchema(element)).thenReturn(DUMMY_SCHEMA);

    final var schemaResponse = resolver.resolveAdHocToolsSchema(List.of(element));
    assertThat(schemaResponse.toolDefinitions())
        .singleElement()
        .satisfies(
            toolDefinition -> {
              assertThat(toolDefinition.name()).isEqualTo("Test_Tool");
              assertThat(toolDefinition.description()).isEqualTo("Test Tool");
              assertThat(toolDefinition.inputSchema()).isEqualTo(DUMMY_SCHEMA);
            });
  }

  @Test
  void categorizesIntoGatewayAndNonGatewayDefinitions() {
    final var elements =
        List.of(
            AdHocToolElement.builder().elementId("A").elementName("Tool A").build(),
            AdHocToolElement.builder().elementId("B").elementName("Tool B").build(),
            AdHocToolElement.builder()
                .elementId("GW_A_1")
                .elementName("Gateway Type A 1")
                .properties(Map.of(GatewayToolDefinitionResolver.GATEWAY_TYPE_EXTENSION, "A"))
                .build(),
            AdHocToolElement.builder().elementId("C").elementName("Tool C").build(),
            AdHocToolElement.builder()
                .elementId("GW_B_1")
                .elementName("Gateway Type B 1")
                .documentation("Gateway Type B 1 documentation")
                .properties(Map.of(GatewayToolDefinitionResolver.GATEWAY_TYPE_EXTENSION, "B"))
                .build());

    final var resolver =
        new AdHocToolsSchemaResolverImpl(
            List.of(
                new TypePropertyBasedGatewayToolDefinitionResolver("A"),
                new TypePropertyBasedGatewayToolDefinitionResolver("B")),
            schemaGenerator);

    final var schemaResponse = resolver.resolveAdHocToolsSchema(elements);

    assertThat(schemaResponse.toolDefinitions())
        .hasSize(3)
        .extracting("name")
        .containsExactly("A", "B", "C");

    assertThat(schemaResponse.gatewayToolDefinitions())
        .hasSize(2)
        .satisfiesExactly(
            gw -> {
              assertThat(gw.type()).isEqualTo("A");
              assertThat(gw.name()).isEqualTo("GW_A_1");
              assertThat(gw.description()).isEqualTo("Gateway Type A 1");
            },
            gw -> {
              assertThat(gw.type()).isEqualTo("B");
              assertThat(gw.name()).isEqualTo("GW_B_1");
              assertThat(gw.description()).isEqualTo("Gateway Type B 1 documentation");
            });
  }
}
