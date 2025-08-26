/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.schema;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import java.util.Map;

/**
 * Generates a JSON schema from an identified ad-hoc tool element including parameter definitions
 * resolved from tagging functions such as fromAi().
 */
public interface AdHocToolSchemaGenerator {
  Map<String, Object> generateToolSchema(AdHocToolElement element);
}
