/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import io.camunda.connectors.soap.SoapConnectorInput.Authentication;
import io.camunda.connectors.soap.SoapConnectorInput.Authentication.Signature;
import io.camunda.connectors.soap.SoapConnectorInput.Authentication.Signature.Certificate.SingleCertificate;
import io.camunda.connectors.soap.SoapConnectorInput.Authentication.UsernameToken;
import io.camunda.connectors.soap.SoapConnectorInput.YesNo;
import org.junit.jupiter.api.Test;

class SoapCredentialTest {

  private static final String REST =
      """
      "serviceUrl": "https://example.com/service",
      "soapVersion": {"version": "1.1"},
      "header": {"type": "none"},
      "body": {"type": "json", "json": {}}
      """;

  private static final String USERNAME_TOKEN_CREDENTIAL =
      """
      "soapCredential": {"authentication": {"type": "usernameToken",
        "username": "credential-user", "password": "{{secrets.SOAP_PASSWORD}}",
        "encoded": "No"}},
      """;

  private SoapConnectorInput bind(String authentication) {
    return OutboundConnectorContextBuilder.create()
        .validation(new DefaultValidationProvider())
        .secret("SOAP_PASSWORD", "resolved-password")
        .variables("{" + authentication + REST + "}")
        .build()
        .bindVariables(SoapConnectorInput.class);
  }

  @Test
  void usesCredential_whenOnlyCredentialBound() {
    var input = bind(USERNAME_TOKEN_CREDENTIAL);

    assertThat(input.authentication())
        .isEqualTo(new UsernameToken("credential-user", "resolved-password", YesNo.No));
  }

  @Test
  void usesInlineAuthentication_whenNoCredentialBound() {
    var input =
        bind(
            """
            "authentication": {"authentication": "usernameToken",
              "username": "inline", "password": "p", "encoded": "Yes"},
            """);

    assertThat(input.authentication()).isEqualTo(new UsernameToken("inline", "p", YesNo.Yes));
  }

  @Test
  void credentialWins_whenBothPresent() {
    var input =
        bind(
            USERNAME_TOKEN_CREDENTIAL
                + """
                "authentication": {"authentication": "usernameToken",
                  "username": "inline", "password": "p", "encoded": "Yes"},
                """);

    assertThat(input.authentication())
        .isEqualTo(new UsernameToken("credential-user", "resolved-password", YesNo.No));
  }

  @Test
  void ignoresLeftoverInlineDiscriminator_whenCredentialBound() {
    // Modeler keeps the hidden discriminator default, without the fields it would require
    var input =
        bind(
            USERNAME_TOKEN_CREDENTIAL + "\"authentication\": {\"authentication\": \"signature\"},");

    assertThat(input.authentication()).isInstanceOf(UsernameToken.class);
  }

  @Test
  void bindsSignatureCredentialAsSingleCertificateSignature() {
    var input =
        bind(
            """
            "soapCredential": {"authentication": {"type": "signature",
              "certificate": "CERT", "privateKey": "KEY",
              "signatureAlgorithm": "sig-alg", "digestAlgorithm": "digest-alg", "timestamp": 30}},
            """);

    assertThat(input.authentication())
        .isEqualTo(
            new Signature(new SingleCertificate("CERT", "KEY"), "sig-alg", "digest-alg", 30, null));
  }

  @Test
  void rejectsKeystoreCertificateInCredential() {
    assertThatThrownBy(
            () ->
                bind(
                    """
                    "soapCredential": {"authentication": {"type": "keystore",
                      "keystoreLocation": "/k.jks", "keystorePassword": "p",
                      "alias": "a", "password": "p"}},
                    """))
        .hasMessageContaining("Could not resolve type id 'keystore'");
  }

  @Test
  void rejectsNoneInCredential() {
    assertThatThrownBy(
            () -> bind("\"soapCredential\": {\"authentication\": {\"type\": \"none\"}},"))
        .hasMessageContaining("Could not resolve type id 'none'");
  }

  @Test
  void validatesInlineAuthentication_whenNoCredentialBound() {
    assertThatThrownBy(() -> bind("\"authentication\": {\"authentication\": \"usernameToken\"},"))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("authentication.username");
  }

  @Test
  void validatesCredentialAuthentication() {
    assertThatThrownBy(
            () ->
                bind(
                    """
                    "soapCredential": {"authentication": {"type": "signature",
                      "certificate": "CERT"}},
                    """))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("privateKey");
  }

  @Test
  void failsNamingBothSources_whenNeitherPresent() {
    assertThatThrownBy(() -> bind(""))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining(
            "No authentication provided by the reusable credential or the element template");
  }

  @Test
  void credentialAuthenticationIsAnAuthentication() {
    Authentication authentication =
        new SoapConnectorInput.CredentialAuthentication.SingleCertificateSignature(
                "CERT", "KEY", null, null, null)
            .toAuthentication();

    assertThat(authentication).isInstanceOf(Signature.class);
  }
}
