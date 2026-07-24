/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.http.base.model.auth.BearerAuthentication;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.runtime.test.outbound.TestValidationProvider;
import org.junit.jupiter.api.Test;

/**
 * Guards the {@code @Valid} cascade on the bound credential: validation must descend through
 * {@code authenticationConfiguration -> RestAuthenticationConfiguration.authentication ->
 * BearerAuthentication} so a credential carrying a blank token (which {@code @NotEmpty} forbids)
 * is rejected the same way inline authentication would be. Runs through the real runtime
 * validation path ({@code bindProperties}).
 */
class PollingRuntimePropertiesCredentialValidationTest {

  @Test
  void credentialWithBlankTokenIsRejectedByCascadingValidation() {
    String properties =
        """
        {
          "url": "http://localhost:8085/http-endpoint",
          "method": "GET",
          "authenticationConfiguration": {
            "authentication": { "type": "bearer", "token": "" }
          }
        }
        """;
    var context = InboundConnectorContextBuilder.create()
            .properties(properties)
            .validation(new TestValidationProvider())
            .build();

    assertThatThrownBy(() -> context.bindProperties(PollingRuntimeProperties.class))
        .hasMessageContaining("token");
  }

  @Test
  void credentialWithValidTokenDoesNotTripAuthValidation() {
    String properties =
        """
        {
          "url": "http://localhost:8085/http-endpoint",
          "method": "GET",
          "authenticationConfiguration": {
            "authentication": { "type": "bearer", "token": "valid-token" }
          }
        }
        """;
    var context = InboundConnectorContextBuilder.create()
            .properties(properties)
            .validation(new TestValidationProvider())
            .build();

    var boundProperties = context.bindProperties(PollingRuntimeProperties.class);
    assertThat(boundProperties.getAuthentication()).isInstanceOf(BearerAuthentication.class);
  }
}
