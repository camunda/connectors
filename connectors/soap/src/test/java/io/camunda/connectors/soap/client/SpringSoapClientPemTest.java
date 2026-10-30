/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.camunda.connectors.soap.CertificateUtil;
import io.camunda.connectors.soap.CertificateUtil.CertificateHolder;
import io.camunda.connectors.soap.SoapConnectorInput.Authentication.Signature;
import io.camunda.connectors.soap.SoapConnectorInput.Authentication.Signature.Certificate.SingleCertificate;
import io.camunda.connectors.soap.SoapConnectorInput.Version._1_2;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Single-line inputs (such as Hub's credential form) flatten a pasted PEM. The client must still be
 * able to sign with it, using the certificate that was entered.
 */
class SpringSoapClientPemTest {

  private static final String RESPONSE =
      "<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\">"
          + "<env:Body><pong xmlns=\"urn:test\"/></env:Body></env:Envelope>";

  enum PemFormat {
    WELL_FORMED(pem -> pem),
    FLATTENED_ONTO_ONE_LINE(pem -> pem.replaceAll("\\s*\\R\\s*", "")),
    LINE_BREAKS_AS_SPACES(pem -> pem.trim().replaceAll("\\s*\\R\\s*", " ")),
    LITERAL_BACKSLASH_N(pem -> pem.trim().replaceAll("\\s*\\R\\s*", "\\\\n"));

    private final UnaryOperator<String> mangle;

    PemFormat(UnaryOperator<String> mangle) {
      this.mangle = mangle;
    }
  }

  private HttpServer server;
  private final AtomicReference<String> receivedRequest = new AtomicReference<>();

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          receivedRequest.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] response = RESPONSE.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/soap+xml");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @ParameterizedTest
  @EnumSource(PemFormat.class)
  void signsRequestWithTheEnteredCertificate(PemFormat format) throws Exception {
    CertificateHolder holder = CertificateUtil.generateSelfSignedCertificate();
    var entered =
        new SingleCertificate(
            format.mangle.apply(holder.certificate()), format.mangle.apply(holder.privateKey()));

    new SpringSoapClient()
        .sendSoapRequest(
            "http://localhost:" + server.getAddress().getPort() + "/",
            new _1_2(),
            null,
            "<ping xmlns=\"urn:test\"/>",
            new Signature(entered, null, null, null, null),
            5,
            Map.of());

    String request = receivedRequest.get();
    assertThat(request).contains("SignatureValue");
    assertThat(extractBinarySecurityToken(request))
        .isEqualTo(encodeCertificateAsBase64Der(holder.certificate()));
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
}
