/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.schema;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;
import java.util.List;

/**
 * Converts a list of {@link AdHocToolElement} to a {@link AdHocToolsSchemaResponse} containing tool
 * definitions and gateway tool definitions.
 */
public interface AdHocToolsSchemaResolver {
  AdHocToolsSchemaResponse resolveAdHocToolsSchema(List<AdHocToolElement> elements);
}
