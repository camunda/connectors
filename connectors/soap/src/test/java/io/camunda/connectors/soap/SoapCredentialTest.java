/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import io.camunda.connectors.soap.CertificateUtil.CertificateHolder;
import io.camunda.connectors.soap.SoapConnectorInput.Authentication;
import io.camunda.connectors.soap.SoapConnectorInput.Authentication.Signature;
import io.camunda.connectors.soap.SoapConnectorInput.Authentication.Signature.Certificate.SingleCertificate;
import io.camunda.connectors.soap.SoapConnectorInput.Authentication.UsernameToken;
import io.camunda.connectors.soap.SoapConnectorInput.YesNo;
import io.camunda.connectors.soap.client.SpringSoapClient;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
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
    assertThatThrownBy(() ->
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
    assertThatThrownBy(() ->
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
  void resolvesCertificateSecretInSignatureCredential_andSignsRequest() throws Exception {
    CertificateHolder holder = CertificateUtil.generateSelfSignedCertificate();

    var receivedRequest = new AtomicReference<String>();
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          receivedRequest.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] response =
              ("<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\">"
                      + "<env:Body><pong xmlns=\"urn:test\"/></env:Body></env:Envelope>")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/soap+xml");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      String variables =
          """
          {"soapCredential": {"authentication": {"type": "signature",
            "certificate": "{{secrets.SOAP_CERTIFICATE}}",
            "privateKey": "{{secrets.SOAP_PRIVATE_KEY}}"}},
          "serviceUrl": "http://localhost:%d/",
          "soapVersion": {"version": "1.2"},
          "header": {"type": "none"},
          "body": {"type": "json", "json": {}}}\
          """
              .formatted(server.getAddress().getPort());
      SoapConnectorInput input =
          OutboundConnectorContextBuilder.create()
              .validation(new DefaultValidationProvider())
              .secret("SOAP_CERTIFICATE", holder.certificate())
              .secret("SOAP_PRIVATE_KEY", holder.privateKey())
              .variables(variables)
              .build()
              .bindVariables(SoapConnectorInput.class);

      assertThat(input.authentication())
          .isEqualTo(
              new Signature(
                  new SingleCertificate(holder.certificate(), holder.privateKey()),
                  null,
                  null,
                  null,
                  null));

      new SpringSoapClient()
          .sendSoapRequest(
              input.serviceUrl(),
              input.soapVersion(),
              null,
              "<ping xmlns=\"urn:test\"/>",
              input.authentication(),
              5,
              Map.of());

      String request = receivedRequest.get();
      assertThat(request).contains("SignatureValue");
      assertThat(extractBinarySecurityToken(request))
          .isEqualTo(encodeCertificateAsBase64Der(holder.certificate()));
    } finally {
      server.stop(0);
    }
  }

  private static String extractBinarySecurityToken(String request) {
    var matcher =
        Pattern.compile("<[^>]*BinarySecurityToken[^>]*>([^<]+)</", Pattern.DOTALL)
            .matcher(request);
    assertThat(matcher.find()).as("BinarySecurityToken in request").isTrue();
    return matcher.group(1).replaceAll("\\s+", "");
  }

  private static String encodeCertificateAsBase64Der(String certificatePem) throws Exception {
    var certificate =
        CertificateFactory.getInstance("X.509")
            .generateCertificate(
                new ByteArrayInputStream(certificatePem.getBytes(StandardCharsets.UTF_8)));
    return Base64.getEncoder().encodeToString(certificate.getEncoded());
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
