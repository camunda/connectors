/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import io.camunda.connector.impl.feel.FEEL;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record JWTProperties(
    @FEEL List<String> requiredPermissions,
    Function<Object, List<String>> permissionsExpression,
    @FEEL String jwkUrl) {
  public JWTProperties {
    Objects.requireNonNull(jwkUrl);
  }
}
