/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.discovery;

import io.camunda.connector.agenticai.adhoctoolsschema.schema.TypePropertyBasedGatewayToolDefinitionResolver;

public class A2aGatewayToolDefinitionResolver
    extends TypePropertyBasedGatewayToolDefinitionResolver {

  public A2aGatewayToolDefinitionResolver() {
    super(A2aGatewayToolHandler.GATEWAY_TYPE);
  }
}
