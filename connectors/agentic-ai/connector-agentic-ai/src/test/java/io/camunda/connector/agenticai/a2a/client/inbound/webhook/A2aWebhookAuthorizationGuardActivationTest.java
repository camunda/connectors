/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.inbound.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.a2a.client.common.convert.A2aSdkObjectConverter;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.inbound.authorization.WebhookAuthorizationGuard;
import io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the security-testing-findings#266 fix, applied to the A2A client webhook
 * connector: it must not activate when every permissive layer combines (no authorization together
 * with HMAC verification disabled), unless an operator explicitly opts in. Mirrors {@code
 * WebhookAuthorizationGuardActivationTest} in the connector-webhook module so both webhook-family
 * connectors are held to the same activation rule.
 */
class A2aWebhookAuthorizationGuardActivationTest {

  @AfterEach
  void clearOptIn() {
    System.clearProperty(WebhookAuthorizationGuard.ALLOW_UNAUTHENTICATED_PROPERTY);
  }

  private static A2aClientWebhookExecutable newExecutable() {
    return new A2aClientWebhookExecutable(mock(A2aSdkObjectConverter.class), new ObjectMapper());
  }

  private static InboundConnectorContext contextWith(Map<String, Object> inboundOverrides) {
    Map<String, Object> inbound = new HashMap<>();
    inbound.put("context", "a2aWebhookContext");
    inbound.put("clientResponse", "=task");
    inbound.putAll(inboundOverrides);
    return InboundConnectorContextBuilder.create().properties(Map.of("inbound", inbound)).build();
  }

  @Test
  void activate_explicitNoneAuthAndHmacDisabled_isRejected() {
    var ctx =
        contextWith(
            Map.of(
                "auth",
                Map.of("type", "NONE"),
                "shouldValidateHmac",
                HMACSwitchCustomerChoice.disabled.name()));

    var exception = catchException(() -> newExecutable().activate(ctx));

    assertThat(exception)
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("no authorization")
        .hasMessageContaining(WebhookAuthorizationGuard.ALLOW_UNAUTHENTICATED_PROPERTY);
  }

  @Test
  void activate_authUnset_isRejected() {
    // The wrapper constructor normalizes a null 'auth' to None() before the @NotNull constraint on
    // the field ever runs, so hand-authored BPMN that never binds 'auth' is as open as one that
    // explicitly sets it to NONE.
    var ctx = contextWith(Map.of("shouldValidateHmac", HMACSwitchCustomerChoice.disabled.name()));

    var exception = catchException(() -> newExecutable().activate(ctx));

    assertThat(exception).isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void activate_noneAuthWithHmacEnabled_isAllowed() {
    var ctx =
        contextWith(
            Map.of(
                "auth",
                Map.of("type", "NONE"),
                "shouldValidateHmac",
                HMACSwitchCustomerChoice.enabled.name(),
                "hmacSecret",
                "secret",
                "hmacHeader",
                "X-Hmac"));

    assertThat(catchException(() -> newExecutable().activate(ctx))).isNull();
  }

  @Test
  void activate_apiKeyAuthWithHmacDisabled_isAllowed() {
    var ctx =
        contextWith(
            Map.of(
                "auth",
                Map.of(
                    "type", "APIKEY",
                    "apiKey", "myApiKey",
                    "apiKeyLocator", "=request.headers.authorization"),
                "shouldValidateHmac",
                HMACSwitchCustomerChoice.disabled.name()));

    assertThat(catchException(() -> newExecutable().activate(ctx))).isNull();
  }

  @Test
  void activate_unauthenticatedCombinationWithOperatorOptIn_isAllowed() {
    System.setProperty(WebhookAuthorizationGuard.ALLOW_UNAUTHENTICATED_PROPERTY, "true");
    var ctx =
        contextWith(
            Map.of(
                "auth",
                Map.of("type", "NONE"),
                "shouldValidateHmac",
                HMACSwitchCustomerChoice.disabled.name()));

    assertThat(catchException(() -> newExecutable().activate(ctx))).isNull();
  }
}
