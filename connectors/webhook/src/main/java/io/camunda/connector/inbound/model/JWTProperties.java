/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public record JWTProperties(
    List<String> requiredPermissions,
    Function<Object, List<String>> jwtRoleExpression,
    Map<String, String> headers) {
  public JWTProperties {
    Objects.requireNonNull(requiredPermissions);
    Objects.requireNonNull(jwtRoleExpression);
    Objects.requireNonNull(headers);
  }
}
