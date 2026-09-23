/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

public class JWTPropertiesTest {

  @Test
  public void rejectsNullIssuer() {
    assertThatThrownBy(
            () -> new JWTProperties("https://mockUrl.com", null, "api1", null, List.of("admin")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void rejectsBlankIssuer() {
    assertThatThrownBy(
            () -> new JWTProperties("https://mockUrl.com", "   ", "api1", null, List.of("admin")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void rejectsNullAudience() {
    assertThatThrownBy(
            () ->
                new JWTProperties(
                    "https://mockUrl.com", "https://idp.local", null, null, List.of("admin")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void rejectsBlankAudience() {
    assertThatThrownBy(
            () ->
                new JWTProperties(
                    "https://mockUrl.com", "https://idp.local", "   ", null, List.of("admin")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
