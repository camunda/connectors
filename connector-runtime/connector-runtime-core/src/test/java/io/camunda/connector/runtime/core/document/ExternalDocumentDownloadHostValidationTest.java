/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.core.document;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.connector.runtime.core.intrinsic.DisabledIntrinsicFunctionExecutor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The host check runs where the external document is downloaded, so it applies whatever route the
 * reference took, including untrusted process data bound to a connector property.
 */
@WireMockTest
class ExternalDocumentDownloadHostValidationTest {

  private static final String SECRET = "internal-secret";

  private String baseUrl;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMock) {
    baseUrl = wireMock.getHttpBaseUrl();
    stubFor(get("/secret").willReturn(ok(SECRET)));
  }

  @Test
  void defaultFactoryRefusesToDownloadFromLoopback() {
    var factory = new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
    var document = factory.resolve(new ExternalReference(baseUrl + "/secret"));

    assertThatThrownBy(document::asByteArray)
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("The external document URL is not allowed");
    verify(0, getRequestedFor(urlEqualTo("/secret")));
  }

  @Test
  void factoryDownloadsWhenThePolicyAllowsTheHost() {
    var factory =
        new DocumentFactoryImpl(
            InMemoryDocumentStore.INSTANCE, new ExternalDocumentHostPolicy(true, true));
    var document = factory.resolve(new ExternalReference(baseUrl + "/secret"));

    assertThat(new String(document.asByteArray(), StandardCharsets.UTF_8)).isEqualTo(SECRET);
  }

  @Test
  void redirectsAreNotFollowed() {
    stubFor(
        get("/redirect")
            .willReturn(aResponse().withStatus(302).withHeader("Location", baseUrl + "/secret")));
    var factory =
        new DocumentFactoryImpl(
            InMemoryDocumentStore.INSTANCE, new ExternalDocumentHostPolicy(true, true));
    var document = factory.resolve(new ExternalReference(baseUrl + "/redirect"));

    try {
      document.asByteArray();
    } catch (RuntimeException ignored) {
      // only whether the redirect target was requested matters here
    }

    verify(1, getRequestedFor(urlEqualTo("/redirect")));
    verify(0, getRequestedFor(urlEqualTo("/secret")));
  }

  @Test
  void externalReferenceInProcessDataBoundToAStringPropertyIsNotDownloaded() {
    var objectMapper = objectMapperWith(new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE));
    var processData =
        """
        {"value": {"camunda.document.type": "external", "url": "%s/secret", "name": "x"}}
        """
            .formatted(baseUrl);

    assertThatThrownBy(() -> objectMapper.readValue(processData, StringProperty.class))
        .hasCauseInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("The external document URL is not allowed");
    verify(0, getRequestedFor(urlEqualTo("/secret")));
  }

  @Test
  void externalReferenceBoundToAStringPropertyIsDownloadedWhenThePolicyAllowsTheHost()
      throws Exception {
    var objectMapper =
        objectMapperWith(
            new DocumentFactoryImpl(
                InMemoryDocumentStore.INSTANCE, new ExternalDocumentHostPolicy(true, true)));
    var processData =
        """
        {"value": {"camunda.document.type": "external", "url": "%s/secret", "name": "x"}}
        """
            .formatted(baseUrl);

    var result = objectMapper.readValue(processData, StringProperty.class);

    assertThat(result.value())
        .isEqualTo(Base64.getEncoder().encodeToString(SECRET.getBytes(StandardCharsets.UTF_8)));
  }

  private static ObjectMapper objectMapperWith(DocumentFactoryImpl documentFactory) {
    var copy = ConnectorsObjectMapperSupplier.getCopy();
    return copy.registerModule(
        new JacksonModuleDocumentDeserializer(
            documentFactory,
            new DisabledIntrinsicFunctionExecutor(),
            DocumentModuleSettings.create()));
  }

  record StringProperty(String value) {}

  record ExternalReference(String url) implements ExternalDocumentReference {
    @Override
    public String name() {
      return "x";
    }
  }
}
