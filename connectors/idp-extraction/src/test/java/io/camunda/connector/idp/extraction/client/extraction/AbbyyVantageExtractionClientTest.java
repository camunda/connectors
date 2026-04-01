/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.client.extraction;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.client.extraction.base.TextExtractor;
import io.camunda.connector.idp.extraction.model.abbyy.AbbyyTransactionResponse;
import io.camunda.connector.idp.extraction.model.abbyy.AbbyyTransactionResponse.AbbyyDocument;
import io.camunda.connector.idp.extraction.model.abbyy.AbbyyTransactionResponse.AbbyyResultFile;
import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbbyyVantageExtractionClientTest {

  private static final String BASE_URL = "https://vantage-us.abbyy.com";

  @Mock private Document mockDocument;
  @Mock private DocumentMetadata mockMetadata;
  @Mock private HttpClient mockHttpClient;

  @SuppressWarnings("unchecked")
  private HttpResponse<String> mockResponse(int statusCode, String body) {
    HttpResponse<String> response = Mockito.mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(statusCode);
    when(response.body()).thenReturn(body);
    return response;
  }

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
  class ApiIntegration {

    @Test
    void obtainAccessToken_shouldSucceed() throws Exception {
      doReturn(
              mockResponse(
                  200,
                  """
                  {"access_token": "test-token-123", "token_type": "Bearer", "expires_in": 3600}
                  """))
          .when(mockHttpClient)
          .send(any(HttpRequest.class), any());

      AbbyyVantageExtractionClient client =
          new AbbyyVantageExtractionClient(
              BASE_URL, "client-id", "client-secret", "OCR", mockHttpClient);

      String token = client.obtainAccessToken();

      assertThat(token).isEqualTo("test-token-123");

      ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
      verify(mockHttpClient).send(requestCaptor.capture(), any());

      HttpRequest capturedRequest = requestCaptor.getValue();
      assertThat(capturedRequest.uri().toString()).isEqualTo(BASE_URL + "/auth2/connect/token");
      assertThat(capturedRequest.headers().firstValue("Content-Type"))
          .hasValue("application/x-www-form-urlencoded");
    }

    @Test
    void obtainAccessToken_shouldThrowOnAuthError() throws Exception {
      doReturn(mockResponse(401, "Unauthorized"))
          .when(mockHttpClient)
          .send(any(HttpRequest.class), any());

      AbbyyVantageExtractionClient client =
          new AbbyyVantageExtractionClient(
              BASE_URL, "client-id", "wrong-secret", "OCR", mockHttpClient);

      assertThatThrownBy(client::obtainAccessToken)
          .isInstanceOf(ConnectorException.class)
          .hasMessageContaining("Failed to obtain ABBYY access token");
    }

    @Test
    void launchTransaction_shouldReturnTransactionId() throws Exception {
      doReturn(mockResponse(200, "{\"transactionId\":\"tx-abc-123\"}"))
          .when(mockHttpClient)
          .send(any(HttpRequest.class), any());

      AbbyyVantageExtractionClient client =
          new AbbyyVantageExtractionClient(
              BASE_URL, "client-id", "client-secret", "OCR", mockHttpClient);

      when(mockDocument.asInputStream())
          .thenReturn(new ByteArrayInputStream("fake pdf content".getBytes()));
      when(mockDocument.metadata()).thenReturn(mockMetadata);
      when(mockMetadata.getContentType()).thenReturn("application/pdf");

      String transactionId = client.launchTransaction("test-token", mockDocument);

      assertThat(transactionId).isEqualTo("tx-abc-123");

      ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
      verify(mockHttpClient).send(requestCaptor.capture(), any());

      HttpRequest capturedRequest = requestCaptor.getValue();
      assertThat(capturedRequest.uri().toString())
          .contains("/api/publicapi/v1/transactions/launch")
          .contains("skillId=OCR");
      assertThat(capturedRequest.headers().firstValue("Authorization"))
          .hasValue("Bearer test-token");
    }

    @Test
    void pollUntilProcessed_shouldReturnWhenProcessed() throws Exception {
      doReturn(
              mockResponse(
                  200,
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
                  """))
          .when(mockHttpClient)
          .send(any(HttpRequest.class), any());

      AbbyyVantageExtractionClient client =
          new AbbyyVantageExtractionClient(
              BASE_URL, "client-id", "client-secret", "OCR", mockHttpClient);

      var result = client.pollUntilProcessed("test-token", "tx-123");

      assertThat(result.status()).isEqualTo("Processed");
      assertThat(result.documents()).hasSize(1);
      assertThat(result.documents().getFirst().resultFiles()).hasSize(1);
      assertThat(result.documents().getFirst().resultFiles().getFirst().fileId())
          .isEqualTo("file-1");
    }

    @Test
    void pollUntilProcessed_shouldThrowOnFailed() throws Exception {
      doReturn(
              mockResponse(
                  200,
                  """
                  {"id": "tx-fail", "status": "Failed"}
                  """))
          .when(mockHttpClient)
          .send(any(HttpRequest.class), any());

      AbbyyVantageExtractionClient client =
          new AbbyyVantageExtractionClient(
              BASE_URL, "client-id", "client-secret", "OCR", mockHttpClient);

      assertThatThrownBy(() -> client.pollUntilProcessed("test-token", "tx-fail"))
          .isInstanceOf(ConnectorException.class)
          .hasMessageContaining("failed");
    }

    @Test
    void downloadResultText_shouldFindTextFile() throws Exception {
      doReturn(mockResponse(200, "Extracted document text"))
          .when(mockHttpClient)
          .send(any(HttpRequest.class), any());

      AbbyyVantageExtractionClient client =
          new AbbyyVantageExtractionClient(
              BASE_URL, "client-id", "client-secret", "OCR", mockHttpClient);

      AbbyyTransactionResponse txResponse =
          new AbbyyTransactionResponse(
              "tx-txt",
              "Processed",
              List.of(
                  new AbbyyDocument(
                      "doc-1",
                      List.of(
                          new AbbyyResultFile("file-json", "OcrJson"),
                          new AbbyyResultFile("file-txt", "Text"),
                          new AbbyyResultFile("file-pdf", "Pdf")),
                      null,
                      null)),
              null);

      String result = client.downloadResultText("test-token", txResponse);

      assertThat(result).isEqualTo("Extracted document text");

      ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
      verify(mockHttpClient).send(requestCaptor.capture(), any());

      // Verify it picked the Text file, not the OcrJson or Pdf
      assertThat(requestCaptor.getValue().uri().toString())
          .contains("/transactions/tx-txt/files/file-txt/download");
    }

    @Test
    void downloadResultText_shouldThrowWhenNoTextFile() {
      AbbyyVantageExtractionClient client =
          new AbbyyVantageExtractionClient(
              BASE_URL, "client-id", "client-secret", "OCR", mockHttpClient);

      AbbyyTransactionResponse txResponse =
          new AbbyyTransactionResponse(
              "tx-no-txt",
              "Processed",
              List.of(
                  new AbbyyDocument(
                      "doc-1",
                      List.of(
                          new AbbyyResultFile("file-json", "OcrJson"),
                          new AbbyyResultFile("file-pdf", "Pdf")),
                      null,
                      null)),
              null);

      assertThatThrownBy(() -> client.downloadResultText("test-token", txResponse))
          .isInstanceOf(ConnectorException.class)
          .hasMessageContaining("No Text result file found")
          .hasMessageContaining("configured to output Text format");
    }

    @Test
    void extract_fullFlow_shouldReturnTextDirectly() throws Exception {
      String tokenJson =
          """
          {"access_token": "token-123", "token_type": "Bearer", "expires_in": 3600}
          """;
      String launchJson = "{\"transactionId\":\"tx-full-flow\"}";
      String pollJson =
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
          """;
      String downloadText =
          """
          Invoice #12345
          Camunda Services GmbH
          Berlin, Germany

          Item          Qty    Price
          Widget A       10   $50.00
          Widget B        5   $75.00

          Total: $1,234.56
          """;

      // Pre-create responses to avoid UnfinishedStubbing from nested when() calls
      var tokenResponse = mockResponse(200, tokenJson);
      var launchResponse = mockResponse(200, launchJson);
      var pollResponse = mockResponse(200, pollJson);
      var downloadResponse = mockResponse(200, downloadText);

      // Sequential responses: token → launch → poll → download
      doReturn(tokenResponse)
          .doReturn(launchResponse)
          .doReturn(pollResponse)
          .doReturn(downloadResponse)
          .when(mockHttpClient)
          .send(any(HttpRequest.class), any());

      AbbyyVantageExtractionClient client =
          new AbbyyVantageExtractionClient(
              BASE_URL, "client-id", "client-secret", "OCR", mockHttpClient);

      when(mockDocument.asInputStream())
          .thenReturn(new ByteArrayInputStream("fake pdf".getBytes()));
      when(mockDocument.metadata()).thenReturn(mockMetadata);
      when(mockMetadata.getContentType()).thenReturn("application/pdf");

      String result = client.extract(mockDocument);

      assertThat(result).contains("Invoice #12345");
      assertThat(result).contains("Camunda Services GmbH");
      assertThat(result).contains("Total: $1,234.56");
      assertThat(result).contains("Widget A");
    }
  }
}
