/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.ActivityBuilder;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.inbound.HttpWebhookExecutable;
import io.camunda.connector.inbound.model.WebhookAuthorization;
import io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the security-testing-findings#266 fix: a webhook must not activate when
 * every permissive layer combines (no authorization together with HMAC verification disabled),
 * unless an operator explicitly opts in.
 */
class WebhookAuthorizationGuardActivationTest {

  @AfterEach
  void clearOptIn() {
    System.clearProperty(WebhookAuthorizationGuard.ALLOW_UNAUTHENTICATED_PROPERTY);
  }

  private static InboundConnectorContext contextWith(Map<String, Object> inboundOverrides) {
    Map<String, Object> inbound = new HashMap<>();
    inbound.put("context", "webhookContext");
    inbound.put("method", "any");
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

    var exception = catchException(() -> new HttpWebhookExecutable().activate(ctx));

    assertThat(exception)
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("no authorization")
        .hasMessageContaining(WebhookAuthorizationGuard.ALLOW_UNAUTHENTICATED_PROPERTY);
  }

  @Test
  void activate_authAndHmacBothUnset_isRejected() {
    // Hand-authored BPMN with neither property bound: 'auth' is null and normalized to None() by
    // the wrapper constructor; 'shouldValidateHmac' stays null and is treated as not-enabled by the
    // runtime's HMAC gate. Both permissive layers are present without ever writing
    // "NONE"/"disabled"
    // explicitly, which is exactly the gap the issue calls out.
    var ctx = contextWith(Map.of());

    var exception = catchException(() -> new HttpWebhookExecutable().activate(ctx));

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

    assertThat(catchException(() -> new HttpWebhookExecutable().activate(ctx))).isNull();
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

    assertThat(catchException(() -> new HttpWebhookExecutable().activate(ctx))).isNull();
  }

  @Test
  void activate_unauthenticatedCombinationWithOperatorOptIn_isAllowedAndLogsWarning() {
    System.setProperty(WebhookAuthorizationGuard.ALLOW_UNAUTHENTICATED_PROPERTY, "true");
    InboundConnectorContext context = mock(InboundConnectorContext.class);
    AtomicReference<Activity> loggedActivity = new AtomicReference<>();
    doAnswer(
            invocation -> {
              Consumer<ActivityBuilder> consumer = invocation.getArgument(0);
              ActivityBuilder builder = Activity.newBuilder();
              consumer.accept(builder);
              loggedActivity.set(builder.build());
              return null;
            })
        .when(context)
        .log(any(Consumer.class));

    var exception =
        catchException(
            () ->
                WebhookAuthorizationGuard.rejectUnauthenticatedActivation(
                    context, new WebhookAuthorization.None(), HMACSwitchCustomerChoice.disabled));

    assertThat(exception).isNull();
    assertThat(loggedActivity.get()).isNotNull();
    assertThat(loggedActivity.get().severity()).isEqualTo(Severity.WARNING);
    assertThat(loggedActivity.get().tag()).isEqualTo("webhook-authorization");
    assertThat(loggedActivity.get().message())
        .contains(WebhookAuthorizationGuard.ALLOW_UNAUTHENTICATED_PROPERTY);
  }
}
