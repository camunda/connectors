/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.resolver;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;

/**
 * Resolves the schema for ad-hoc tools based on the process definition key and ad-hoc subprocess
 * ID.
 *
 * <p>This includes: - resolving individual tool definitions and their schema from activities within
 * an ad-hoc sub-process - resolving gateway tool definitions using specific gateway resolvers (e.g.
 * MCP)
 */
public interface AdHocToolsSchemaResolver {
  AdHocToolsSchemaResponse resolveSchema(Long processDefinitionKey, String adHocSubProcessId);
}
