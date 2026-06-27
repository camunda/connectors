/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.discovery;

import io.camunda.connector.agenticai.adhoctoolsschema.schema.TypePropertyBasedGatewayToolDefinitionResolver;

/**
 * Resolves sandbox gateway tool definitions from ad-hoc sub-process elements whose {@code
 * io.camunda.agenticai.gateway.type} extension property equals {@code "sandbox"}.
 */
public class SandboxGatewayToolDefinitionResolver
    extends TypePropertyBasedGatewayToolDefinitionResolver {

  public SandboxGatewayToolDefinitionResolver() {
    super(SandboxGatewayToolHandler.GATEWAY_TYPE);
  }
}
