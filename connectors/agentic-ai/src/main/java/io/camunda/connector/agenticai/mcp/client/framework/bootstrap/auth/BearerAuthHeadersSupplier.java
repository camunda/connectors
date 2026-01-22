/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.bootstrap.auth;

import io.camunda.connector.agenticai.mcp.client.model.auth.BearerAuthentication;
import java.util.Map;
import java.util.function.Supplier;

public class BearerAuthHeadersSupplier implements Supplier<Map<String, String>> {

  private final BearerAuthentication bearerAuthentication;

  public BearerAuthHeadersSupplier(BearerAuthentication bearerAuthentication) {
    this.bearerAuthentication = bearerAuthentication;
  }

  @Override
  public Map<String, String> get() {
    return Map.of("Authorization", "Bearer " + bearerAuthentication.token());
  }
}
