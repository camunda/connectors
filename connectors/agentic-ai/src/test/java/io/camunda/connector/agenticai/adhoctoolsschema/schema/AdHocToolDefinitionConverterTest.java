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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdHocToolDefinitionConverterTest {

  private static final Map<String, Object> DUMMY_SCHEMA = Map.of("type", "dummy");

  @Mock private AdHocToolSchemaGenerator schemaGenerator;
  @InjectMocks private AdHocToolDefinitionConverterImpl converter;

  @Test
  void createsToolDefinitionFromElementWithDocumentation() {
    final var element =
        AdHocToolElement.builder()
            .elementId("Test_Tool")
            .elementName("Test Tool")
            .documentation("This is a test tool.")
            .build();

    when(schemaGenerator.generateToolSchema(element)).thenReturn(DUMMY_SCHEMA);

    final var toolDefinition = converter.createToolDefinition(element);

    assertThat(toolDefinition.name()).isEqualTo("Test_Tool");
    assertThat(toolDefinition.description()).isEqualTo("This is a test tool.");
    assertThat(toolDefinition.inputSchema()).isEqualTo(DUMMY_SCHEMA);
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

    final var toolDefinition = converter.createToolDefinition(element);

    assertThat(toolDefinition.name()).isEqualTo("Test_Tool");
    assertThat(toolDefinition.description()).isEqualTo("Test Tool");
    assertThat(toolDefinition.inputSchema()).isEqualTo(DUMMY_SCHEMA);
  }
}
