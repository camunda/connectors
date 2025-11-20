/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.auth;

import static io.camunda.connector.http.client.authentication.Base64Helper.buildBasicAuthenticationHeader;

import io.camunda.connector.agenticai.mcp.client.model.auth.BasicAuthentication;
import java.util.Map;
import java.util.function.Supplier;

public class BasicAuthHeadersSupplier implements Supplier<Map<String, String>> {

  private final BasicAuthentication basicAuthentication;

  public BasicAuthHeadersSupplier(BasicAuthentication basicAuthentication) {
    this.basicAuthentication = basicAuthentication;
  }

  @Override
  public Map<String, String> get() {
    return Map.of(
        "Authorization",
        buildBasicAuthenticationHeader(
            basicAuthentication.username(), basicAuthentication.password()));
  }
}
