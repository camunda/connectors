/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.box.model.BoxRequest;
import io.camunda.connector.box.model.BoxRequest.Authentication;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import org.junit.jupiter.api.Test;

class BoxCredentialTest {

  private static final String OPERATION =
      """
      "operation": {"type": "deleteFile", "filePath": "/a.txt"}
      """;

  private static final String ENTERPRISE_CREDENTIAL =
      """
      "boxCredential": {"authentication": {"type": "clientCredentialsEnterprise",
        "clientId": "credential-client", "clientSecret": "{{secrets.BOX_SECRET}}",
        "enterpriseId": "enterprise-1"}},
      """;

  private BoxRequest bind(String authentication) {
    return OutboundConnectorContextBuilder.create()
        .validation(new DefaultValidationProvider())
        .secret("BOX_SECRET", "resolved-secret")
        .variables("{" + authentication + OPERATION + "}")
        .build()
        .bindVariables(BoxRequest.class);
  }

  @Test
  void usesCredential_whenOnlyCredentialBound() {
    var request = bind(ENTERPRISE_CREDENTIAL);

    assertThat(request.authentication())
        .isEqualTo(
            new Authentication.ClientCredentialsEnterprise(
                "credential-client", "resolved-secret", "enterprise-1"));
  }

  @Test
  void usesInlineAuthentication_whenNoCredentialBound() {
    var request =
        bind("\"authentication\": {\"type\": \"developerToken\", \"accessToken\": \"inline\"},");

    assertThat(request.authentication()).isEqualTo(new Authentication.DeveloperToken("inline"));
  }

  @Test
  void credentialWins_whenBothPresent() {
    var request =
        bind(
            ENTERPRISE_CREDENTIAL
                + "\"authentication\": {\"type\": \"developerToken\", \"accessToken\": \"inline\"},");

    assertThat(request.authentication())
        .isInstanceOf(Authentication.ClientCredentialsEnterprise.class);
  }

  @Test
  void ignoresLeftoverInlineDiscriminator_whenCredentialBound() {
    // Modeler keeps the hidden discriminator default, without the fields it would require
    var request =
        bind(ENTERPRISE_CREDENTIAL + "\"authentication\": {\"type\": \"developerToken\"},");

    assertThat(request.authentication())
        .isInstanceOf(Authentication.ClientCredentialsEnterprise.class);
  }

  @Test
  void bindsEveryCredentialVariant() {
    assertThat(
            bind("""
                    "boxCredential": {"authentication": {"type": "clientCredentialsUser",
                      "clientId": "c", "clientSecret": "s", "userId": "u"}},
                    """)
                .authentication())
        .isEqualTo(new Authentication.ClientCredentialsUser("c", "s", "u"));
    assertThat(
            bind("""
                    "boxCredential": {"authentication": {"type": "jwtJsonConfig",
                      "jsonConfig": "{}"}},
                    """)
                .authentication())
        .isEqualTo(new Authentication.JWTJsonConfig("{}"));
  }

  @Test
  void rejectsDeveloperTokenInCredential() {
    assertThatThrownBy(
            () ->
                bind(
                    """
                    "boxCredential": {"authentication": {"type": "developerToken",
                      "accessToken": "token"}},
                    """))
        .hasMessageContaining("Could not resolve type id 'developerToken'");
  }

  @Test
  void validatesInlineAuthentication_whenNoCredentialBound() {
    assertThatThrownBy(() -> bind("\"authentication\": {\"type\": \"developerToken\"},"))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("accessToken");
  }

  @Test
  void validatesCredentialAuthentication() {
    assertThatThrownBy(
            () ->
                bind(
                    """
                    "boxCredential": {"authentication": {"type": "clientCredentialsEnterprise",
                      "clientId": "c", "clientSecret": "s"}},
                    """))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("enterpriseId");
  }

  @Test
  void failsNamingBothSources_whenNeitherPresent() {
    assertThatThrownBy(() -> bind(""))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining(
            "No authentication provided by the reusable credential or the element template");
  }
}
