/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.resolver.schema;

import io.camunda.connector.api.error.ConnectorException;

public class AdHocToolSchemaGenerationException extends ConnectorException {
  private static final String ERROR_CODE_AD_HOC_TOOL_SCHEMA_INVALID = "AD_HOC_TOOL_SCHEMA_INVALID";

  public AdHocToolSchemaGenerationException(String message) {
    super(ERROR_CODE_AD_HOC_TOOL_SCHEMA_INVALID, message);
  }
}
