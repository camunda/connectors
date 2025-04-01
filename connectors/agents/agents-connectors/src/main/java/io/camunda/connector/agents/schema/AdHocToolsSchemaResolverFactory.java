/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agents.schema;

import io.camunda.connector.agents.core.AgentsApplicationContextHolder;

public class AdHocToolsSchemaResolverFactory {

  private AdHocToolsSchemaResolverFactory() {}

  public static AdHocToolsSchemaResolver schemaResolverFromStaticContext() {
    return new CamundaClientAdHocToolsSchemaResolver(
        AgentsApplicationContextHolder.currentContext().camundaClient());
  }

  public static AdHocToolsSchemaResolver cachingSchemaResolverFromStaticContext() {
    return new CachingAdHocToolsSchemaResolver(schemaResolverFromStaticContext());
  }
}
