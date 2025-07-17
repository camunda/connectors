/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolver;

public class AdHocToolsSchemaExecutor {
  private final ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
  private final AdHocToolsSchemaResolver toolsSchemaResolver;

  public AdHocToolsSchemaExecutor(
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver,
      AdHocToolsSchemaResolver toolsSchemaResolver) {
    this.toolElementsResolver = toolElementsResolver;
    this.toolsSchemaResolver = toolsSchemaResolver;
  }

  public AdHocToolsSchemaResponse resolveAdHocToolsSchema(
      Long processDefinitionKey, String adHocSubProcessId) {
    final var elements =
        toolElementsResolver.resolveToolElements(processDefinitionKey, adHocSubProcessId);
    return toolsSchemaResolver.resolveAdHocToolsSchema(elements);
  }
}
