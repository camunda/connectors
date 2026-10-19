/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.blobstorage.model.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.azure.blobstorage.model.request.auth.OAuthAuthentication;
import io.camunda.connector.azure.blobstorage.model.request.auth.SASAuthentication;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import org.junit.jupiter.api.Test;

class BlobStorageRequestCredentialTest {

  private static final String OPERATION =
      """
      "operationDiscriminator":"downloadBlob",\
      "operation":{"container":"my-container","fileName":"my-file.txt"}\
      """;

  private static final String OAUTH_CREDENTIAL =
      """
      "authenticationConfiguration":{"authentication":{"type":"oAuth-client-credentials-flow",\
      "tenantId":"cred-tenant","clientId":"cred-client","clientSecret":"cred-secret",\
      "accountUrl":"https://credaccount.blob.core.windows.net"}}\
      """;

  private static final String INLINE_SAS =
      """
      "authentication":{"type":"SAS","SASToken":"inline-token",\
      "SASUrl":"https://inlineaccount.blob.core.windows.net"}\
      """;

  private static BlobStorageRequest bind(String variables) {
    return OutboundConnectorContextBuilder.create()
        .variables(variables)
        .validation(new DefaultValidationProvider())
        .build()
        .bindVariables(BlobStorageRequest.class);
  }

  @Test
  void credentialSuppliesAuthenticationIncludingTheAccountUrl() {
    var request = bind("{" + OAUTH_CREDENTIAL + "," + OPERATION + "}");

    assertThat(request.getAuthentication())
        .isInstanceOfSatisfying(
            OAuthAuthentication.class,
            auth -> {
              assertThat(auth.tenantId()).isEqualTo("cred-tenant");
              assertThat(auth.accountUrl()).isEqualTo("https://credaccount.blob.core.windows.net");
            });
  }

  @Test
  void inlineAuthenticationIsUsedWhenNoCredentialIsBound() {
    var request = bind("{" + INLINE_SAS + "," + OPERATION + "}");

    assertThat(request.getAuthentication())
        .isInstanceOfSatisfying(
            SASAuthentication.class, auth -> assertThat(auth.SASToken()).isEqualTo("inline-token"));
  }

  @Test
  void credentialWinsWhenBothArePresent() {
    var request = bind("{" + OAUTH_CREDENTIAL + "," + INLINE_SAS + "," + OPERATION + "}");

    assertThat(request.getAuthentication()).isInstanceOf(OAuthAuthentication.class);
  }

  @Test
  void bindingFailsNamingBothSourcesWhenNeitherIsPresent() {
    assertThatThrownBy(() -> bind("{" + OPERATION + "}"))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("credential")
        .hasMessageContaining("element template");
  }

  @Test
  void validationCascadesIntoTheBoundCredential() {
    String credentialWithoutAccountUrl =
        """
        "authenticationConfiguration":{"authentication":{"type":"oAuth-client-credentials-flow",\
        "tenantId":"cred-tenant","clientId":"cred-client","clientSecret":"cred-secret",\
        "accountUrl":""}}\
        """;

    assertThatThrownBy(() -> bind("{" + credentialWithoutAccountUrl + "," + OPERATION + "}"))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("accountUrl");
  }

  @Test
  void leftoverInlineDiscriminatorDoesNotFailValidationWhenCredentialIsBound() {
    // Modeler leaves the inline authentication's default discriminator (and no other inline
    // fields, since they're hidden once a credential is bound) in the job input regardless of
    // which source the user picked.
    String leftoverInlineSas =
        """
        "authentication":{"type":"SAS"}\
        """;

    var request = bind("{" + OAUTH_CREDENTIAL + "," + leftoverInlineSas + "," + OPERATION + "}");

    assertThat(request.getAuthentication())
        .isInstanceOfSatisfying(
            OAuthAuthentication.class,
            auth -> assertThat(auth.tenantId()).isEqualTo("cred-tenant"));
  }

  @Test
  void toStringRedactsTheSecretsOfBothAuthenticationTypes() {
    var sas =
        new SASAuthentication(
            "the-sas-token", "https://account.blob.core.windows.net/?sig=the-signature");
    var oAuth =
        new OAuthAuthentication(
            "tenant-id", "client-id", "the-secret", "https://account.blob.core.windows.net");

    assertThat(sas.toString())
        .doesNotContain("the-sas-token", "sig=the-signature")
        .contains("[REDACTED]");
    assertThat(oAuth.toString()).doesNotContain("the-secret").contains("[REDACTED]");
  }
}
