/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.camunda.connectors.soap.SoapConnectorInput.Authentication.None;
import io.camunda.connectors.soap.SoapConnectorInput.Version;
import io.camunda.connectors.soap.SoapConnectorInput.Version._1_1;
import io.camunda.connectors.soap.SoapConnectorInput.Version._1_2;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * XML that declares a DOCTYPE must never be processed, whether it comes from the process (SOAP body
 * and header templates) or from the SOAP service (responses and faults). The out-of-band server
 * records any external entity or DTD the parser tries to fetch.
 */
class SpringSoapClientXxeTest {

  private static final String SOAP_11_NS = "http://schemas.xmlsoap.org/soap/envelope/";
  private static final String SOAP_12_NS = "http://www.w3.org/2003/05/soap-envelope";
  private static final String OUT_OF_BAND_URL = "{{outOfBandUrl}}";

  private HttpServer soapServer;
  private HttpServer outOfBandServer;
  private final List<String> soapRequests = new CopyOnWriteArrayList<>();
  private final List<String> outOfBandRequests = new CopyOnWriteArrayList<>();
  private final AtomicReference<String> responseBody = new AtomicReference<>();
  private final AtomicReference<String> responseContentType = new AtomicReference<>();
  private final AtomicInteger responseStatus = new AtomicInteger(200);

  @BeforeEach
  void startServers() throws Exception {
    outOfBandServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    outOfBandServer.createContext(
        "/",
        exchange -> {
          outOfBandRequests.add(exchange.getRequestURI().toString());
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    outOfBandServer.start();

    soapServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    soapServer.createContext(
        "/",
        exchange -> {
          soapRequests.add(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", responseContentType.get());
          exchange.sendResponseHeaders(responseStatus.get(), body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    soapServer.start();
    respondWith(new _1_2(), 200, envelope(SOAP_12_NS, "<pong xmlns=\"urn:test\"/>"));
  }

  @AfterEach
  void stopServers() {
    soapServer.stop(0);
    outOfBandServer.stop(0);
  }

  @Test
  void rejectsDoctypeInBodyTemplate() {
    assertThatThrownBy(() -> send(new _1_2(), null, maliciousFragment()))
        .hasStackTraceContaining("DOCTYPE is disallowed");

    assertThat(soapRequests).isEmpty();
    assertThat(outOfBandRequests).isEmpty();
  }

  @Test
  void rejectsDoctypeInHeaderTemplate() {
    assertThatThrownBy(() -> send(new _1_2(), maliciousFragment(), "<ping xmlns=\"urn:test\"/>"))
        .hasStackTraceContaining("DOCTYPE is disallowed");

    assertThat(soapRequests).isEmpty();
    assertThat(outOfBandRequests).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("maliciousResponses")
  void rejectsDoctypeInResponse(Version version, int status, String response) {
    respondWith(version, status, response.replace(OUT_OF_BAND_URL, outOfBandUrl()));

    assertThatThrownBy(() -> send(version, null, "<ping xmlns=\"urn:test\"/>"))
        .hasStackTraceContaining("Document Type Declaration is not allowed");

    assertThat(soapRequests).hasSize(1);
    assertThat(outOfBandRequests).isEmpty();
  }

  @Test
  void acceptsResponseWithoutDoctype() throws Exception {
    String response = send(new _1_2(), null, "<ping xmlns=\"urn:test\"/>");

    assertThat(response).contains("pong");
  }

  static Stream<Arguments> maliciousResponses() {
    String soap11Fault =
        "<env:Fault><faultcode>env:Server</faultcode><faultstring>&xxe;</faultstring></env:Fault>";
    String soap12Fault =
        "<env:Fault><env:Code><env:Value>env:Receiver</env:Value></env:Code>"
            + "<env:Reason><env:Text xml:lang=\"en\">&xxe;</env:Text></env:Reason></env:Fault>";
    return Stream.of(
        Arguments.of(new _1_1(null), 200, maliciousEnvelope(SOAP_11_NS, "<pong>&xxe;</pong>")),
        Arguments.of(new _1_2(), 200, maliciousEnvelope(SOAP_12_NS, "<pong>&xxe;</pong>")),
        Arguments.of(new _1_1(null), 500, maliciousEnvelope(SOAP_11_NS, soap11Fault)),
        Arguments.of(new _1_2(), 500, maliciousEnvelope(SOAP_12_NS, soap12Fault)));
  }

  private String send(Version version, String header, String body) throws Exception {
    return new SpringSoapClient()
        .sendSoapRequest(
            "http://localhost:" + soapServer.getAddress().getPort() + "/",
            version,
            header,
            body,
            new None(),
            5,
            Map.of());
  }

  private void respondWith(Version version, int status, String body) {
    responseStatus.set(status);
    responseContentType.set(version instanceof _1_1 ? "text/xml" : "application/soap+xml");
    responseBody.set(body);
  }

  private String outOfBandUrl() {
    return "http://localhost:" + outOfBandServer.getAddress().getPort();
  }

  private String maliciousFragment() {
    return "<?xml version=\"1.0\"?><!DOCTYPE ping [<!ENTITY % dtd SYSTEM \""
        + outOfBandUrl()
        + "/evil.dtd\">%dtd;]><ping xmlns=\"urn:test\"/>";
  }

  private static String maliciousEnvelope(String namespace, String content) {
    return "<?xml version=\"1.0\"?><!DOCTYPE env:Envelope [<!ENTITY xxe SYSTEM \""
        + OUT_OF_BAND_URL
        + "/entity\">]>"
        + envelope(namespace, content);
  }

  private static String envelope(String namespace, String content) {
    return "<env:Envelope xmlns:env=\""
        + namespace
        + "\"><env:Body>"
        + content
        + "</env:Body></env:Envelope>";
  }
}
