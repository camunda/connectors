/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.client.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.client.extraction.base.TextExtractor;
import io.camunda.connector.idp.extraction.model.abbyy.AbbyyTokenResponse;
import io.camunda.connector.idp.extraction.model.abbyy.AbbyyTransactionResponse;
import io.camunda.connector.idp.extraction.model.abbyy.AbbyyTransactionResponse.AbbyyDocument;
import io.camunda.connector.idp.extraction.model.abbyy.AbbyyTransactionResponse.AbbyyResultFile;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbbyyVantageExtractionClient implements TextExtractor, AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbbyyVantageExtractionClient.class);
  private static final long POLLING_INTERVAL_MS = 2000;
  private static final long MAX_POLLING_DURATION_MS = 5 * 60 * 1000L; // 5 minutes max
  private static final String PROCESSED_STATUS = "Processed";
  private static final String FAILED_STATUS = "Failed";
  private static final String CANCELED_STATUS = "Canceled";

  private final String baseUrl;
  private final String clientId;
  private final String clientSecret;
  private final String skillId;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public AbbyyVantageExtractionClient(
      String baseUrl, String clientId, String clientSecret, String skillId) {
    this(
        baseUrl,
        clientId,
        clientSecret,
        skillId,
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build());
  }

  AbbyyVantageExtractionClient(
      String baseUrl, String clientId, String clientSecret, String skillId, HttpClient httpClient) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.skillId = skillId;
    this.httpClient = httpClient;
    this.objectMapper = ConnectorsObjectMapperSupplier.getCopy();
  }

  @Override
  public void close() {
    httpClient.close();
    LOGGER.debug("AbbyyVantageExtractionClient closed");
  }

  @Override
  public String extract(Document document) {
    try {
      String accessToken = obtainAccessToken();
      String transactionId = launchTransaction(accessToken, document);
      AbbyyTransactionResponse completedTransaction =
          pollUntilProcessed(accessToken, transactionId);
      return downloadResultText(accessToken, completedTransaction);
    } catch (ConnectorException e) {
      throw e;
    } catch (Exception e) {
      LOGGER.error("Error extracting text via ABBYY Vantage", e);
      throw new ConnectorException(
          "ABBYY_EXTRACTION_ERROR",
          "Failed to extract text via ABBYY Vantage: " + e.getMessage(),
          e);
    }
  }

  String obtainAccessToken() throws IOException, InterruptedException {
    String tokenUrl = baseUrl + "/auth2/connect/token";

    String formBody =
        "grant_type=client_credentials"
            + "&client_id="
            + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
            + "&client_secret="
            + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
            + "&scope="
            + URLEncoder.encode("openid permissions global.wildcard", StandardCharsets.UTF_8);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formBody))
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      throw new ConnectorException(
          "ABBYY_AUTH_ERROR",
          "Failed to obtain ABBYY access token. Status: "
              + response.statusCode()
              + ", Body: "
              + response.body());
    }

    AbbyyTokenResponse tokenResponse =
        objectMapper.readValue(response.body(), AbbyyTokenResponse.class);
    LOGGER.debug("Successfully obtained ABBYY access token");
    return tokenResponse.accessToken();
  }

  String launchTransaction(String accessToken, Document document)
      throws IOException, InterruptedException {
    String apiBase = baseUrl + "/api/publicapi/v1";
    String launchUrl =
        apiBase
            + "/transactions/launch?skillId="
            + URLEncoder.encode(skillId, StandardCharsets.UTF_8);

    String contentType =
        document.metadata() != null && document.metadata().getContentType() != null
            ? document.metadata().getContentType()
            : "application/pdf";

    String fileName = "document-" + UUID.randomUUID();
    String boundary = UUID.randomUUID().toString();

    try (InputStream multipartStream =
        buildMultipartStream(boundary, fileName, contentType, document.asInputStream())) {

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(launchUrl))
              .header("Authorization", "Bearer " + accessToken)
              .header("Content-Type", "multipart/form-data; boundary=" + boundary)
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofInputStream(() -> multipartStream))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200 && response.statusCode() != 201) {
        throw new ConnectorException(
            "ABBYY_LAUNCH_ERROR",
            "Failed to launch ABBYY transaction. Status: "
                + response.statusCode()
                + ", Body: "
                + response.body());
      }

      String transactionId;
      String responseBody = response.body().trim();
      var jsonNode = objectMapper.readTree(responseBody);
      if (jsonNode.has("transactionId")) {
        transactionId = jsonNode.get("transactionId").asText();
      } else {
        // Fallback: plain string response (strip quotes)
        transactionId = responseBody.replace("\"", "");
      }
      LOGGER.debug("Launched ABBYY transaction: {}", transactionId);
      return transactionId;
    }
  }

  AbbyyTransactionResponse pollUntilProcessed(String accessToken, String transactionId)
      throws InterruptedException {
    String apiBase = baseUrl + "/api/publicapi/v1";
    String statusUrl = apiBase + "/transactions/" + transactionId;

    CompletableFuture<AbbyyTransactionResponse> future = new CompletableFuture<>();
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    try {
      scheduler.scheduleWithFixedDelay(
          () -> {
            try {
              HttpRequest request =
                  HttpRequest.newBuilder()
                      .uri(URI.create(statusUrl))
                      .header("Authorization", "Bearer " + accessToken)
                      .header("Accept", "application/json")
                      .GET()
                      .build();

              HttpResponse<String> response =
                  httpClient.send(request, HttpResponse.BodyHandlers.ofString());

              if (response.statusCode() != 200) {
                future.completeExceptionally(
                    new ConnectorException(
                        "ABBYY_STATUS_ERROR",
                        "Failed to get ABBYY transaction status. Status: "
                            + response.statusCode()
                            + ", Body: "
                            + response.body()));
                return;
              }

              AbbyyTransactionResponse txResponse =
                  objectMapper.readValue(response.body(), AbbyyTransactionResponse.class);

              String status = txResponse.status();
              LOGGER.debug("ABBYY transaction {} status: {}", transactionId, status);

              if (PROCESSED_STATUS.equals(status)) {
                future.complete(txResponse);
              } else if (FAILED_STATUS.equals(status)) {
                future.completeExceptionally(
                    new ConnectorException(
                        "ABBYY_PROCESSING_FAILED",
                        "ABBYY transaction " + transactionId + " failed"));
              } else if (CANCELED_STATUS.equals(status)) {
                future.completeExceptionally(
                    new ConnectorException(
                        "ABBYY_PROCESSING_CANCELED",
                        "ABBYY transaction " + transactionId + " was canceled"));
              }
            } catch (ConnectorException e) {
              future.completeExceptionally(e);
            } catch (Exception e) {
              future.completeExceptionally(
                  new ConnectorException(
                      "ABBYY_STATUS_ERROR",
                      "Error polling ABBYY transaction status: " + e.getMessage(),
                      e));
            }
          },
          0,
          POLLING_INTERVAL_MS,
          TimeUnit.MILLISECONDS);

      return future.get(MAX_POLLING_DURATION_MS, TimeUnit.MILLISECONDS);
    } catch (java.util.concurrent.TimeoutException e) {
      throw new ConnectorException(
          "ABBYY_POLLING_TIMEOUT",
          "ABBYY transaction " + transactionId + " did not complete within the timeout period");
    } catch (java.util.concurrent.ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof ConnectorException ce) {
        throw ce;
      }
      throw new ConnectorException(
          "ABBYY_STATUS_ERROR", "Error polling ABBYY transaction: " + cause.getMessage(), cause);
    } finally {
      scheduler.shutdownNow();
    }
  }

  String downloadResultText(String accessToken, AbbyyTransactionResponse transaction)
      throws IOException, InterruptedException {
    String apiBase = baseUrl + "/api/publicapi/v1";

    // Find the Text/Txt result file
    String fileId = null;
    if (transaction.documents() != null) {
      for (AbbyyDocument doc : transaction.documents()) {
        if (doc.resultFiles() != null) {
          for (AbbyyResultFile rf : doc.resultFiles()) {
            LOGGER.debug("ABBYY result file: fileId={}, type={}", rf.fileId(), rf.type());
          }
          for (AbbyyResultFile resultFile : doc.resultFiles()) {
            if (resultFile.type() != null
                && (resultFile.type().equalsIgnoreCase("Text")
                    || resultFile.type().equalsIgnoreCase("Txt"))) {
              fileId = resultFile.fileId();
              break;
            }
          }
        }
        if (fileId != null) break;
      }
    }

    if (fileId == null) {
      throw new ConnectorException(
          "ABBYY_NO_RESULT",
          "No Text result file found in ABBYY transaction "
              + transaction.id()
              + ". Ensure the ABBYY skill is configured to output Text format.");
    }

    String downloadUrl =
        apiBase + "/transactions/" + transaction.id() + "/files/" + fileId + "/download";

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(downloadUrl))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      throw new ConnectorException(
          "ABBYY_DOWNLOAD_ERROR",
          "Failed to download ABBYY result file. Status: "
              + response.statusCode()
              + ", Body: "
              + response.body());
    }

    LOGGER.debug("Downloaded ABBYY result text for transaction {}", transaction.id());
    return response.body();
  }

  private InputStream buildMultipartStream(
      String boundary, String fileName, String contentType, InputStream fileContent) {
    String crlf = "\r\n";
    String header =
        "--"
            + boundary
            + crlf
            + "Content-Disposition: form-data; name=\"Files\"; filename=\""
            + fileName
            + "\""
            + crlf
            + "Content-Type: "
            + contentType
            + crlf
            + crlf;
    String footer = crlf + "--" + boundary + "--" + crlf;

    return new SequenceInputStream(
        Collections.enumeration(
            List.of(
                new ByteArrayInputStream(header.getBytes(StandardCharsets.UTF_8)),
                fileContent,
                new ByteArrayInputStream(footer.getBytes(StandardCharsets.UTF_8)))));
  }
}
