/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.client.extraction;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.client.extraction.base.TextExtractor;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbbyyVantageExtractionClientTest {

  @Mock private Document mockDocument;
  @Mock private DocumentMetadata mockMetadata;

  @Nested
  class ConstructorAndLifecycle {

    @Test
    void constructor_shouldConfigureCorrectly() {
      assertThatCode(
              () ->
                  new AbbyyVantageExtractionClient(
                      "https://vantage-us.abbyy.com", "client-id", "client-secret", "OCR"))
          .doesNotThrowAnyException();
    }

    @Test
    void constructor_shouldTrimTrailingSlashFromBaseUrl() {
      AbbyyVantageExtractionClient client =
          new AbbyyVantageExtractionClient(
              "https://vantage-us.abbyy.com/", "client-id", "client-secret", "OCR");
      assertThatCode(client::close).doesNotThrowAnyException();
    }

    @Test
    void close_shouldCompleteSuccessfully() {
      AbbyyVantageExtractionClient client =
          new AbbyyVantageExtractionClient(
              "https://vantage-us.abbyy.com", "client-id", "client-secret", "OCR");
      assertThatCode(client::close).doesNotThrowAnyException();
    }

    @Test
    void shouldImplementTextExtractor() {
      assertThat(TextExtractor.class).isAssignableFrom(AbbyyVantageExtractionClient.class);
    }

    @Test
    void shouldImplementAutoCloseable() {
      assertThat(AutoCloseable.class).isAssignableFrom(AbbyyVantageExtractionClient.class);
    }
  }

  @Nested
  @WireMockTest
  class ApiIntegration {

    @Test
    void obtainAccessToken_shouldSucceed(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      String baseUrl = wmRuntimeInfo.getHttpBaseUrl();

      stubFor(
          post(urlPathEqualTo("/auth2/connect/token"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          """
                          {
                            "access_token": "test-token-123",
                            "token_type": "Bearer",
                            "expires_in": 3600
                          }
                          """)));

      AbbyyVantageExtractionClient client =
          new AbbyyVantageExtractionClient(baseUrl, "client-id", "client-secret", "OCR");

      String token = client.obtainAccessToken();

      assertThat(token).isEqualTo("test-token-123");
      verify(
          postRequestedFor(urlPathEqualTo("/auth2/connect/token"))
              .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
              .withRequestBody(containing("grant_type=client_credentials"))
              .withRequestBody(containing("client_id=client-id"))
              .withRequestBody(containing("client_secret=client-secret")));
    }

    @Test
    void obtainAccessToken_shouldThrowOnAuthError(WireMockRuntimeInfo wmRuntimeInfo) {
      String baseUrl = wmRuntimeInfo.getHttpBaseUrl();

      stubFor(
          post(urlPathEqualTo("/auth2/connect/token"))
              .willReturn(aResponse().withStatus(401).withBody("Unauthorized")));

      AbbyyVantageExtractionClient client =
          new AbbyyVantageExtractionClient(baseUrl, "client-id", "wrong-secret", "OCR");

      assertThatThrownBy(client::obtainAccessToken)
          .isInstanceOf(ConnectorException.class)
          .hasMessageContaining("Failed to obtain ABBYY access token");
    }

    @Test
    void launchTransaction_shouldReturnTransactionId(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      String baseUrl = wmRuntimeInfo.getHttpBaseUrl();

      stubFor(
          post(urlPathEqualTo("/api/publicapi/v1/transactions/launch"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"transactionId\":\"tx-abc-123\"}")));

      AbbyyVantageExtractionClient client =
          new AbbyyVantageExtractionClient(baseUrl, "client-id", "client-secret", "OCR");

      Mockito.when(mockDocument.asInputStream())
          .thenReturn(new ByteArrayInputStream("fake pdf content".getBytes()));
      Mockito.when(mockDocument.metadata()).thenReturn(mockMetadata);
      Mockito.when(mockMetadata.getContentType()).thenReturn("application/pdf");

      String transactionId = client.launchTransaction("test-token", mockDocument);

      assertThat(transactionId).isEqualTo("tx-abc-123");
    }

    @Test
    void pollUntilProcessed_shouldReturnWhenProcessed(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      String baseUrl = wmRuntimeInfo.getHttpBaseUrl();

      stubFor(
          get(urlPathEqualTo("/api/publicapi/v1/transactions/tx-123"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          """
                          {
                            "id": "tx-123",
                            "status": "Processed",
                            "documents": [
                              {
                                "id": "doc-1",
                                "resultFiles": [
                                  {"fileId": "file-1", "type": "Text"}
                                ]
                              }
                            ]
                          }
                          """)));

      AbbyyVantageExtractionClient client =
          new AbbyyVantageExtractionClient(baseUrl, "client-id", "client-secret", "OCR");

      var result = client.pollUntilProcessed("test-token", "tx-123");

      assertThat(result.status()).isEqualTo("Processed");
      assertThat(result.documents()).hasSize(1);
      assertThat(result.documents().getFirst().resultFiles()).hasSize(1);
      assertThat(result.documents().getFirst().resultFiles().getFirst().fileId()).isEqualTo("file-1");
    }

    @Test
    void pollUntilProcessed_shouldThrowOnFailed(WireMockRuntimeInfo wmRuntimeInfo) {
      String baseUrl = wmRuntimeInfo.getHttpBaseUrl();

      stubFor(
          get(urlPathEqualTo("/api/publicapi/v1/transactions/tx-fail"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          """
                          {
                            "id": "tx-fail",
                            "status": "Failed"
                          }
                          """)));

      AbbyyVantageExtractionClient client =
          new AbbyyVantageExtractionClient(baseUrl, "client-id", "client-secret", "OCR");

      assertThatThrownBy(() -> client.pollUntilProcessed("test-token", "tx-fail"))
          .isInstanceOf(ConnectorException.class)
          .hasMessageContaining("failed");
    }

    @Test
    void downloadResultText_shouldFindTextFile(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      String baseUrl = wmRuntimeInfo.getHttpBaseUrl();

      stubFor(
          get(urlPathEqualTo("/api/publicapi/v1/transactions/tx-txt"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          """
                          {
                            "id": "tx-txt",
                            "status": "Processed",
                            "documents": [
                              {
                                "id": "doc-1",
                                "resultFiles": [
                                  {"fileId": "file-json", "type": "OcrJson"},
                                  {"fileId": "file-txt", "type": "Text"},
                                  {"fileId": "file-pdf", "type": "Pdf"}
                                ]
                              }
                            ]
                          }
                          """)));

      stubFor(
          get(urlPathEqualTo("/api/publicapi/v1/transactions/tx-txt/files/file-txt/download"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "text/plain")
                      .withBody("Extracted document text")));

      AbbyyVantageExtractionClient client =
          new AbbyyVantageExtractionClient(baseUrl, "client-id", "client-secret", "OCR");

      var txResponse = client.pollUntilProcessed("test-token", "tx-txt");
      String result = client.downloadResultText("test-token", txResponse);

      assertThat(result).isEqualTo("Extracted document text");

      // Verify it picked the Text file, not the OcrJson or Pdf
      verify(
          getRequestedFor(
              urlPathEqualTo("/api/publicapi/v1/transactions/tx-txt/files/file-txt/download")));
    }

    @Test
    void downloadResultText_shouldThrowWhenNoTextFile(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      String baseUrl = wmRuntimeInfo.getHttpBaseUrl();

      stubFor(
          get(urlPathEqualTo("/api/publicapi/v1/transactions/tx-no-txt"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          """
                          {
                            "id": "tx-no-txt",
                            "status": "Processed",
                            "documents": [
                              {
                                "id": "doc-1",
                                "resultFiles": [
                                  {"fileId": "file-json", "type": "OcrJson"},
                                  {"fileId": "file-pdf", "type": "Pdf"}
                                ]
                              }
                            ]
                          }
                          """)));

      AbbyyVantageExtractionClient client =
          new AbbyyVantageExtractionClient(baseUrl, "client-id", "client-secret", "OCR");

      var txResponse = client.pollUntilProcessed("test-token", "tx-no-txt");

      assertThatThrownBy(() -> client.downloadResultText("test-token", txResponse))
          .isInstanceOf(ConnectorException.class)
          .hasMessageContaining("No Text result file found")
          .hasMessageContaining("configured to output Text format");
    }

    @Test
    void extract_fullFlow_shouldReturnTextDirectly(WireMockRuntimeInfo wmRuntimeInfo) {
      String baseUrl = wmRuntimeInfo.getHttpBaseUrl();

      stubFor(
          post(urlPathEqualTo("/auth2/connect/token"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          """
                          {"access_token": "token-123", "token_type": "Bearer", "expires_in": 3600}
                          """)));

      stubFor(
          post(urlPathEqualTo("/api/publicapi/v1/transactions/launch"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"transactionId\":\"tx-full-flow\"}")));

      stubFor(
          get(urlPathEqualTo("/api/publicapi/v1/transactions/tx-full-flow"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          """
                          {
                            "id": "tx-full-flow",
                            "status": "Processed",
                            "documents": [
                              {
                                "id": "doc-1",
                                "resultFiles": [{"fileId": "result-txt-1", "type": "Text"}]
                              }
                            ]
                          }
                          """)));

      stubFor(
          get(urlPathEqualTo(
                  "/api/publicapi/v1/transactions/tx-full-flow/files/result-txt-1/download"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "text/plain")
                      .withBody(
                          """
                          Invoice #12345
                          Camunda Services GmbH
                          Berlin, Germany

                          Item          Qty    Price
                          Widget A       10   $50.00
                          Widget B        5   $75.00

                          Total: $1,234.56
                          """)));

      AbbyyVantageExtractionClient client =
          new AbbyyVantageExtractionClient(baseUrl, "client-id", "client-secret", "OCR");

      Mockito.when(mockDocument.asInputStream())
          .thenReturn(new ByteArrayInputStream("fake pdf".getBytes()));
      Mockito.when(mockDocument.metadata()).thenReturn(mockMetadata);
      Mockito.when(mockMetadata.getContentType()).thenReturn("application/pdf");

      String result = client.extract(mockDocument);

      assertThat(result).contains("Invoice #12345");
      assertThat(result).contains("Camunda Services GmbH");
      assertThat(result).contains("Total: $1,234.56");
      assertThat(result).contains("Widget A");
    }
  }
}
