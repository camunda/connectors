/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.schema;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;

public class AdHocToolDefinitionConverterImpl implements AdHocToolDefinitionConverter {
  private final AdHocToolSchemaGenerator schemaGenerator;

  public AdHocToolDefinitionConverterImpl(AdHocToolSchemaGenerator schemaGenerator) {
    this.schemaGenerator = schemaGenerator;
  }

  @Override
  public ToolDefinition createToolDefinition(AdHocToolElement element) {
    return ToolDefinition.builder()
        .name(element.elementId())
        .description(element.documentationWithNameFallback())
        .inputSchema(schemaGenerator.generateToolSchema(element))
        .build();
  }
}
