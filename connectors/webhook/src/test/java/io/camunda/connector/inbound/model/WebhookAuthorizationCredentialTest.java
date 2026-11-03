/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.feel.LocalFeelExpressionEvaluator;
import io.camunda.connector.inbound.HttpWebhookExecutable;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Failure.InvalidCredentials;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Success;
import io.camunda.connector.inbound.authorization.WebhookAuthorizationHandler;
import io.camunda.connector.inbound.model.WebhookConnectorProperties.WebhookConnectorPropertiesWrapper;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Covers the effective-value accessors of the Basic, API key and JWT authorization when a reusable
 * credential and/or inline fields are present, through the real JSON binding path.
 */
class WebhookAuthorizationCredentialTest {

  private static final String CREDENTIAL_VALUE = "from-credential";
  private static final String INLINE_VALUE = "from-element-template";
  private static final String LOCATOR = "=split(request.headers.authorization, \" \")[2]";

  private static InboundConnectorContextBuilder contextFor(String auth) {
    return InboundConnectorContextBuilder.create()
        .properties(
            """
            {
              "inbound": {
                "context": "webhookTest",
                "method": "any",
                "auth": %s
              }
            }
            """
                .formatted(auth));
  }

  private static WebhookAuthorization bind(String auth) {
    var wrapper = contextFor(auth).build().bindProperties(WebhookConnectorPropertiesWrapper.class);
    return new WebhookConnectorProperties(wrapper).auth();
  }

  private static WebhookProcessingPayload payload(String authorizationHeader) {
    var payload = mock(WebhookProcessingPayload.class);
    when(payload.headers())
        .thenReturn(
            Map.of("authorization", authorizationHeader, "Content-Type", "application/json"));
    when(payload.params()).thenReturn(Map.of());
    when(payload.rawBody()).thenReturn("{}".getBytes());
    return payload;
  }

  private static String basic(String username, String password) {
    return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
  }

  @Test
  void basic_credentialAndNoInlineValues_usesTheCredential() {
    var auth =
        (WebhookAuthorization.BasicAuth)
            bind(
                """
                { "type": "BASIC",
                  "basicCredential": { "username": "cred-user", "password": "cred-pass" } }
                """);

    assertThat(auth.username()).isEqualTo("cred-user");
    assertThat(auth.password()).isEqualTo("cred-pass");
    assertThat(
            WebhookAuthorizationHandler.getHandlerForAuth(auth)
                .checkAuthorization(payload(basic("cred-user", "cred-pass"))))
        .isInstanceOf(Success.class);
  }

  @Test
  void basic_noCredentialAndInlineValues_usesTheInlineValues() {
    var auth =
        (WebhookAuthorization.BasicAuth)
            bind(
                """
                { "type": "BASIC", "username": "inline-user", "password": "inline-pass" }
                """);

    assertThat(auth.username()).isEqualTo("inline-user");
    assertThat(auth.password()).isEqualTo("inline-pass");
  }

  @Test
  void basic_bothPresent_theCredentialWins() {
    var auth =
        (WebhookAuthorization.BasicAuth)
            bind(
                """
                { "type": "BASIC", "username": "inline-user", "password": "inline-pass",
                  "basicCredential": { "username": "cred-user", "password": "cred-pass" } }
                """);

    assertThat(auth.username()).isEqualTo("cred-user");
    assertThat(
            WebhookAuthorizationHandler.getHandlerForAuth(auth)
                .checkAuthorization(payload(basic("inline-user", "inline-pass"))))
        .isInstanceOf(InvalidCredentials.class);
  }

  @Test
  void basic_credentialAndInlineFieldsLeftBlankByModeler_usesTheCredential() {
    var auth =
        (WebhookAuthorization.BasicAuth)
            bind(
                """
                { "type": "BASIC", "username": "", "password": "",
                  "basicCredential": { "username": "cred-user", "password": "cred-pass" } }
                """);

    assertThat(auth.username()).isEqualTo("cred-user");
    assertThat(auth.password()).isEqualTo("cred-pass");
  }

  @Test
  void apiKey_credentialSuppliesTheKeyAndTheLocatorStaysInline() {
    var auth =
        (WebhookAuthorization.ApiKeyAuth)
            bind(
                """
                { "type": "APIKEY", "apiKeyLocator": "%s",
                  "apiKeyCredential": { "apiKey": "%s" } }
                """
                    .formatted(LOCATOR.replace("\"", "\\\""), CREDENTIAL_VALUE));

    assertThat(auth.apiKey()).isEqualTo(CREDENTIAL_VALUE);
    assertThat(auth.apiKeyLocator()).isNotNull();
    // the test context does not evaluate FEEL, so the handler runs with a locally evaluated locator
    var feel = new LocalFeelExpressionEvaluator();
    var handler =
        WebhookAuthorizationHandler.getHandlerForAuth(
            new WebhookAuthorization.ApiKeyAuth(
                new WebhookApiKeyConfiguration(auth.apiKey()),
                null,
                request -> feel.evaluate(LOCATOR, request)));
    assertThat(handler.checkAuthorization(payload("Bearer " + CREDENTIAL_VALUE)))
        .isInstanceOf(Success.class);
    assertThat(handler.checkAuthorization(payload("Bearer wrong")))
        .isInstanceOf(InvalidCredentials.class);
  }

  @Test
  void apiKey_noCredentialAndInlineKey_usesTheInlineKey() {
    var auth =
        (WebhookAuthorization.ApiKeyAuth)
            bind(
                """
                { "type": "APIKEY", "apiKey": "%s", "apiKeyLocator": "%s" }
                """
                    .formatted(INLINE_VALUE, LOCATOR.replace("\"", "\\\"")));

    assertThat(auth.apiKey()).isEqualTo(INLINE_VALUE);
  }

  @Test
  void apiKey_bothPresent_theCredentialWins() {
    var auth =
        (WebhookAuthorization.ApiKeyAuth)
            bind(
                """
                { "type": "APIKEY", "apiKey": "%s", "apiKeyLocator": "%s",
                  "apiKeyCredential": { "apiKey": "%s" } }
                """
                    .formatted(INLINE_VALUE, LOCATOR.replace("\"", "\\\""), CREDENTIAL_VALUE));

    assertThat(auth.apiKey()).isEqualTo(CREDENTIAL_VALUE);
  }

  @Test
  void jwt_credentialSuppliesTheVerificationValuesAndTheRolesStayInline() {
    var auth =
        (WebhookAuthorization.JwtAuth)
            bind(
                """
                { "type": "JWT",
                  "jwt": {
                    "requiredPermissions": ["admin"],
                    "jwtCredential": {
                      "jwkUrl": "https://idp.example/jwks",
                      "issuer": "https://idp.example",
                      "audience": "api1" } } }
                """);

    assertThat(auth.jwt().jwkUrl()).isEqualTo("https://idp.example/jwks");
    assertThat(auth.jwt().issuer()).isEqualTo("https://idp.example");
    assertThat(auth.jwt().audience()).isEqualTo("api1");
    assertThat(auth.jwt().requiredPermissions()).containsExactly("admin");
  }

  @Test
  void jwt_noCredentialAndInlineValues_usesTheInlineValues() {
    var auth =
        (WebhookAuthorization.JwtAuth)
            bind(
                """
                { "type": "JWT",
                  "jwt": { "jwkUrl": "https://inline.example/jwks", "issuer": "inline-iss",
                           "audience": "inline-aud" } }
                """);

    assertThat(auth.jwt().jwkUrl()).isEqualTo("https://inline.example/jwks");
    assertThat(auth.jwt().issuer()).isEqualTo("inline-iss");
    assertThat(auth.jwt().audience()).isEqualTo("inline-aud");
  }

  @Test
  void jwt_bothPresent_theCredentialWins() {
    var auth =
        (WebhookAuthorization.JwtAuth)
            bind(
                """
                { "type": "JWT",
                  "jwt": {
                    "jwkUrl": "https://inline.example/jwks", "issuer": "inline-iss",
                    "audience": "inline-aud",
                    "jwtCredential": {
                      "jwkUrl": "https://idp.example/jwks", "issuer": "cred-iss",
                      "audience": "cred-aud" } } }
                """);

    assertThat(auth.jwt().jwkUrl()).isEqualTo("https://idp.example/jwks");
    assertThat(auth.jwt().issuer()).isEqualTo("cred-iss");
    assertThat(auth.jwt().audience()).isEqualTo("cred-aud");
  }

  @Test
  void jwt_neitherPresent_bindingFailsNamingBothSources() {
    assertThatThrownBy(
            () ->
                bind(
                    """
                    { "type": "JWT", "jwt": { "jwkUrl": "https://idp.example/jwks" } }
                    """))
        .hasRootCauseMessage(
            "No issuer provided by the reusable credential or the element template");
  }

  @Test
  void activation_basicWithNeitherSource_stillActivates() {
    // The runtime drops value="" properties, so an unfilled Modeler field arrives as absent.
    var context = contextFor("{ \"type\": \"BASIC\" }").build();

    new HttpWebhookExecutable().activate(context);
  }

  @Test
  void activation_basicWithAbsentPassword_stillActivates() {
    var context = contextFor("{ \"type\": \"BASIC\", \"username\": \"user\" }").build();

    new HttpWebhookExecutable().activate(context);
  }

  @Test
  void activation_basicWithEmptyStringValues_stillActivates() {
    var context =
        contextFor("{ \"type\": \"BASIC\", \"username\": \"user\", \"password\": \"\" }").build();

    new HttpWebhookExecutable().activate(context);
  }

  @Test
  void activation_withACredentialAndNoInlineValues_succeeds() {
    var context =
        contextFor(
                """
                { "type": "BASIC",
                  "basicCredential": { "username": "cred-user", "password": "cred-pass" } }
                """)
            .build();

    new HttpWebhookExecutable().activate(context);
  }

  @Test
  void basicCredentialWithAnEmptyUsername_isRejectedWhenBinding() {
    assertThatThrownBy(
            () ->
                bind(
                    """
                    { "type": "BASIC",
                      "basicCredential": { "username": "", "password": "cred-pass" } }
                    """))
        .hasRootCauseMessage("username is required in the webhook Basic credential");
  }

  @Test
  void basicCredentialWithABlankPassword_isRejected() {
    assertThatThrownBy(() -> new WebhookBasicAuthConfiguration("cred-user", "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("password is required in the webhook Basic credential");
  }

  @Test
  void apiKeyCredentialWithABlankKey_isRejectedWhenBinding() {
    assertThatThrownBy(
            () ->
                bind(
                    """
                    { "type": "APIKEY", "apiKeyLocator": "%s",
                      "apiKeyCredential": { "apiKey": "  " } }
                    """
                        .formatted(LOCATOR.replace("\"", "\\\""))))
        .hasRootCauseMessage("apiKey is required in the webhook API key credential");
  }

  @Test
  void jwtCredentialWithAnEmptyField_isRejectedWhenBinding() {
    assertThatThrownBy(
            () ->
                bind(
                    """
                    { "type": "JWT",
                      "jwt": { "jwtCredential": {
                        "jwkUrl": "", "issuer": "https://idp.example", "audience": "api1" } } }
                    """))
        .hasRootCauseMessage("jwkUrl is required in the webhook JWT credential");
    assertThatThrownBy(() -> new WebhookJwtConfiguration("https://idp.example/jwks", null, "api1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("issuer is required in the webhook JWT credential");
  }

  @Test
  void credentialsDoNotLeakTheirSecretsIntoToString() {
    assertThat(new WebhookBasicAuthConfiguration("user", CREDENTIAL_VALUE).toString())
        .doesNotContain(CREDENTIAL_VALUE)
        .contains("REDACTED");
    assertThat(new WebhookApiKeyConfiguration(CREDENTIAL_VALUE).toString())
        .doesNotContain(CREDENTIAL_VALUE)
        .contains("REDACTED");
    assertThat(
            new WebhookAuthorization.BasicAuth(
                    new WebhookBasicAuthConfiguration("user", CREDENTIAL_VALUE), null, null)
                .toString())
        .doesNotContain(CREDENTIAL_VALUE);
    assertThat(
            new WebhookAuthorization.ApiKeyAuth(
                    new WebhookApiKeyConfiguration(CREDENTIAL_VALUE), null, null)
                .toString())
        .doesNotContain(CREDENTIAL_VALUE);
  }
}
