/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.mcp.client.model.auth.BearerAuthentication;
import org.junit.jupiter.api.Test;

class BearerAuthHeadersSupplierTest {

  @Test
  void shouldReturnBearerTokenHeader() {
    var auth = new BearerAuthentication("my-secret-token");
    var supplier = new BearerAuthHeadersSupplier(auth);

    var headers = supplier.get();

    assertThat(headers).containsEntry("Authorization", "Bearer my-secret-token");
  }

  @Test
  void shouldPassThroughTokenWithoutModification() {
    var token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abc123";
    var auth = new BearerAuthentication(token);
    var supplier = new BearerAuthHeadersSupplier(auth);

    var headers = supplier.get();

    assertThat(headers).containsEntry("Authorization", "Bearer " + token);
  }

  @Test
  void shouldReturnSameHeaderOnMultipleCalls() {
    var auth = new BearerAuthentication("token123");
    var supplier = new BearerAuthHeadersSupplier(auth);

    var headers1 = supplier.get();
    var headers2 = supplier.get();

    assertThat(headers1).isEqualTo(headers2);
  }
}
