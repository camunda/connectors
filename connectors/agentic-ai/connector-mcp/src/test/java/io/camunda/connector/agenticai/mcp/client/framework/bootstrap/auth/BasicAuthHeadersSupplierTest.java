/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.bootstrap.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.mcp.client.model.auth.BasicAuthentication;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class BasicAuthHeadersSupplierTest {

  @Test
  void shouldReturnBasicAuthHeader() {
    var auth = new BasicAuthentication("testuser", "testpass");
    var supplier = new BasicAuthHeadersSupplier(auth);

    var headers = supplier.get();

    assertThat(headers).containsEntry("Authorization", "Basic dGVzdHVzZXI6dGVzdHBhc3M=");
  }

  @Test
  void shouldEncodeCredentialsCorrectly() {
    var auth = new BasicAuthentication("user@example.com", "p@ssw0rd!");
    var supplier = new BasicAuthHeadersSupplier(auth);

    var headers = supplier.get();

    var authHeader = headers.get("Authorization");

    assertThat(authHeader).startsWith("Basic ");
    var encodedPart = authHeader.substring("Basic ".length());
    var decoded = new String(Base64.getDecoder().decode(encodedPart), StandardCharsets.UTF_8);
    assertThat(decoded).isEqualTo("user@example.com:p@ssw0rd!");
  }

  @Test
  void shouldReturnSameHeaderOnMultipleCalls() {
    var auth = new BasicAuthentication("user", "pass");
    var supplier = new BasicAuthHeadersSupplier(auth);

    var headers1 = supplier.get();
    var headers2 = supplier.get();

    assertThat(headers1).isEqualTo(headers2);
  }
}
