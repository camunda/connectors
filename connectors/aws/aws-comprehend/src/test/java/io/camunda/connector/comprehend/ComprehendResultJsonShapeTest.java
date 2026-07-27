/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.comprehend.caller.AsyncComprehendCaller;
import io.camunda.connector.comprehend.caller.SyncComprehendCaller;
import io.camunda.connector.comprehend.model.ComprehendAsyncRequestData;
import io.camunda.connector.comprehend.model.ComprehendDocumentReadAction;
import io.camunda.connector.comprehend.model.ComprehendDocumentReadMode;
import io.camunda.connector.comprehend.model.ComprehendInputFormat;
import io.camunda.connector.comprehend.model.ComprehendSyncRequestData;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.DefaultAwsResponseMetadata;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.comprehend.ComprehendAsyncClient;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.ClassifyDocumentRequest;
import software.amazon.awssdk.services.comprehend.model.ClassifyDocumentResponse;
import software.amazon.awssdk.services.comprehend.model.DocumentClass;
import software.amazon.awssdk.services.comprehend.model.DocumentMetadata;
import software.amazon.awssdk.services.comprehend.model.DocumentTypeListItem;
import software.amazon.awssdk.services.comprehend.model.ErrorsListItem;
import software.amazon.awssdk.services.comprehend.model.ExtractedCharactersListItem;
import software.amazon.awssdk.services.comprehend.model.StartDocumentClassificationJobRequest;
import software.amazon.awssdk.services.comprehend.model.StartDocumentClassificationJobResponse;
import software.amazon.awssdk.services.comprehend.model.WarningsListItem;

/**
 * Golden-JSON shape tests for the AWS Comprehend connector's outbound result.
 *
 * <p>{@link ComprehendConnectorFunction} used to return the raw AWS SDK v1 result objects ({@code
 * ClassifyDocumentResult}, {@code StartDocumentClassificationJobResult}) directly as the process
 * variable payload. AWS SDK v2's model classes ({@link ClassifyDocumentResponse}, {@link
 * StartDocumentClassificationJobResponse}) expose fluent accessors instead of JavaBean getters, so
 * serializing them directly with the connectors' {@code ObjectMapper} (which disables {@code
 * FAIL_ON_EMPTY_BEANS}) would silently produce {@code {}}. {@link ComprehendClassifyResult} and
 * {@link ComprehendClassificationJobResult} map the v2 responses back into connector-owned DTOs.
 * These tests pin the exact v1 JSON shape -- including the {@code sdkResponseMetadata}/{@code
 * sdkHttpMetadata} keys and all explicit nulls -- as the contract the AWS SDK v2 migration must
 * reproduce unchanged.
 */
class ComprehendResultJsonShapeTest {

  private final ObjectMapper productionMapper = ConnectorsObjectMapperSupplier.getCopy();
  private final SyncComprehendCaller syncCaller = new SyncComprehendCaller();
  private final AsyncComprehendCaller asyncCaller = new AsyncComprehendCaller();

  /**
   * Sync classify: a fully populated {@link ClassifyDocumentResponse} as returned by a live,
   * multi-page {@code ClassifyDocument} call against a TEXTRACT-backed custom classifier -- classes
   * (name/score/page), document metadata, document type, warnings, an explicitly empty errors list,
   * and populated SDK response/HTTP metadata.
   */
  @Test
  void classifyDocumentResult_populated_serializesToDocumentedV1JsonShape()
      throws JsonProcessingException {
    // Given a fully populated ClassifyDocumentResponse, as returned by a live ClassifyDocument call
    var responseBuilder =
        ClassifyDocumentResponse.builder()
            .classes(
                DocumentClass.builder().name("POSITIVE").score(0.987654f).page(1).build(),
                DocumentClass.builder().name("NEGATIVE").score(0.012346f).page(1).build())
            .documentMetadata(
                DocumentMetadata.builder()
                    .pages(2)
                    .extractedCharacters(
                        ExtractedCharactersListItem.builder().page(1).count(1200).build(),
                        ExtractedCharactersListItem.builder().page(2).count(980).build())
                    .build())
            .documentType(DocumentTypeListItem.builder().page(1).type("NATIVE_PDF").build())
            .errors(List.<ErrorsListItem>of())
            .warnings(
                WarningsListItem.builder()
                    .page(2)
                    .warnCode("INFERENCING_PLAINTEXT_WITH_NATIVE_TRAINED_MODEL")
                    .warnMessage(
                        "The document was submitted as plain text but the model was trained on"
                            + " native documents.")
                    .build());
    responseBuilder.responseMetadata(
        DefaultAwsResponseMetadata.create(Map.of("AWS_REQUEST_ID", REQUEST_ID)));
    responseBuilder.sdkHttpResponse(
        SdkHttpResponse.builder()
            .statusCode(200)
            .putHeader("Content-Length", "85")
            .putHeader("Content-Type", "application/x-amz-json-1.1")
            .putHeader("x-amzn-RequestId", REQUEST_ID)
            .build());
    ClassifyDocumentResponse response = responseBuilder.build();

    ComprehendClient client = mock(ComprehendClient.class);
    when(client.classifyDocument(any(ClassifyDocumentRequest.class))).thenReturn(response);

    // When the caller maps the client's response through the connector-owned result DTO
    ComprehendClassifyResult actualResult =
        syncCaller.call(client, new ComprehendSyncRequestData("plain text", "endpoint-arn"));

    // Then the JSON produced by the production mapper matches the documented v1 shape exactly,
    // including explicit nulls (labels is null: a multi-class classifier response never populates
    // the multi-label "labels" field).
    JsonNode actual = productionMapper.readTree(productionMapper.writeValueAsString(actualResult));
    String expectedJson =
        """
        {
          "sdkResponseMetadata": { "requestId": "929bf054-193b-48e6-ab80-3aeeb613b415" },
          "sdkHttpMetadata": {
            "httpHeaders": {
              "Content-Length": "85",
              "Content-Type": "application/x-amz-json-1.1",
              "x-amzn-RequestId": "929bf054-193b-48e6-ab80-3aeeb613b415"
            },
            "httpStatusCode": 200,
            "allHttpHeaders": {
              "Content-Length": ["85"],
              "Content-Type": ["application/x-amz-json-1.1"],
              "x-amzn-RequestId": ["929bf054-193b-48e6-ab80-3aeeb613b415"]
            }
          },
          "classes": [
            { "name": "POSITIVE", "score": 0.987654, "page": 1 },
            { "name": "NEGATIVE", "score": 0.012346, "page": 1 }
          ],
          "labels": null,
          "documentMetadata": {
            "pages": 2,
            "extractedCharacters": [
              { "page": 1, "count": 1200 },
              { "page": 2, "count": 980 }
            ]
          },
          "documentType": [
            { "page": 1, "type": "NATIVE_PDF" }
          ],
          "errors": [],
          "warnings": [
            {
              "page": 2,
              "warnCode": "INFERENCING_PLAINTEXT_WITH_NATIVE_TRAINED_MODEL",
              "warnMessage": "The document was submitted as plain text but the model was trained on native documents."
            }
          ]
        }
        """;
    JsonNode expected = productionMapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);
    // Tree equality above is key-order-insensitive; also pin the serialized field order to the
    // documented v1 layout (enforced here via @JsonPropertyOrder on the connector-owned DTOs).
    assertThat(productionMapper.writeValueAsString(actualResult))
        .isEqualTo(productionMapper.writeValueAsString(expected));
  }

  /**
   * Sync classify: the empty/minimal {@link ClassifyDocumentResponse} a mocked (or genuinely
   * near-empty) response yields -- every field, including the inherited SDK metadata, is null.
   */
  @Test
  void classifyDocumentResult_empty_serializesToDocumentedV1JsonShape()
      throws JsonProcessingException {
    // Given a minimal/empty ClassifyDocumentResponse (no field populated)
    var result = ClassifyDocumentResponse.builder().build();

    ComprehendClient client = mock(ComprehendClient.class);
    when(client.classifyDocument(any(ClassifyDocumentRequest.class))).thenReturn(result);

    // When the caller maps the client's response through the connector-owned result DTO
    ComprehendClassifyResult actualResult =
        syncCaller.call(client, new ComprehendSyncRequestData("plain text", "endpoint-arn"));

    // Then every field -- including the two SDK metadata keys -- is serialized as an explicit
    // null (the production mapper disables FAIL_ON_EMPTY_BEANS, so this does not collapse to
    // "{}"), and unset v2 list fields (which never surface as Java null, only as hasXxx()==false)
    // are normalized back to null to match the pre-v2 shape.
    JsonNode actual = productionMapper.readTree(productionMapper.writeValueAsString(actualResult));
    String expectedJson =
        """
        {
          "sdkResponseMetadata": null,
          "sdkHttpMetadata": null,
          "classes": null,
          "labels": null,
          "documentMetadata": null,
          "documentType": null,
          "errors": null,
          "warnings": null
        }
        """;
    JsonNode expected = productionMapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);
    assertThat(productionMapper.writeValueAsString(actualResult))
        .isEqualTo(productionMapper.writeValueAsString(expected));
  }

  /**
   * Async job-start: a populated {@link StartDocumentClassificationJobResponse} as returned by a
   * live {@code StartDocumentClassificationJob} call (job accepted and submitted).
   */
  @Test
  void startDocumentClassificationJobResult_serializesToDocumentedV1JsonShape()
      throws JsonProcessingException {
    // Given a populated StartDocumentClassificationJobResponse, as returned by a live
    // StartDocumentClassificationJob call
    var responseBuilder =
        StartDocumentClassificationJobResponse.builder()
            .jobId("12345678-1234-1234-1234-123456789012")
            .jobArn(
                "arn:aws:comprehend:eu-central-1:123456789012:document-classification-job/12345678-1234-1234-1234-123456789012")
            .jobStatus("SUBMITTED")
            .documentClassifierArn(
                "arn:aws:comprehend:eu-central-1:123456789012:document-classifier/my-classifier");
    responseBuilder.responseMetadata(
        DefaultAwsResponseMetadata.create(Map.of("AWS_REQUEST_ID", REQUEST_ID)));
    StartDocumentClassificationJobResponse response = responseBuilder.build();

    ComprehendAsyncClient client = mock(ComprehendAsyncClient.class);
    when(client.startDocumentClassificationJob(any(StartDocumentClassificationJobRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(response));

    // When the caller maps the client's response through the connector-owned result DTO
    ComprehendClassificationJobResult actualResult =
        asyncCaller.call(client, minimalAsyncRequest());

    // Then the JSON produced by the production mapper matches the documented v1 shape exactly.
    // sdkHttpMetadata stays null here: unlike sdkResponseMetadata (set from the request-id map
    // AWS returns), the SDK only populates it when the client processes a real HTTP response --
    // pinned as null here to document that a caller which does not explicitly set it never sees
    // it magically appear.
    JsonNode actual = productionMapper.readTree(productionMapper.writeValueAsString(actualResult));
    String expectedJson =
        """
        {
          "sdkResponseMetadata": { "requestId": "929bf054-193b-48e6-ab80-3aeeb613b415" },
          "sdkHttpMetadata": null,
          "jobId": "12345678-1234-1234-1234-123456789012",
          "jobArn": "arn:aws:comprehend:eu-central-1:123456789012:document-classification-job/12345678-1234-1234-1234-123456789012",
          "jobStatus": "SUBMITTED",
          "documentClassifierArn": "arn:aws:comprehend:eu-central-1:123456789012:document-classifier/my-classifier"
        }
        """;
    JsonNode expected = productionMapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);
    assertThat(productionMapper.writeValueAsString(actualResult))
        .isEqualTo(productionMapper.writeValueAsString(expected));
  }

  private static final String REQUEST_ID = "929bf054-193b-48e6-ab80-3aeeb613b415";

  private static ComprehendAsyncRequestData minimalAsyncRequest() {
    return new ComprehendAsyncRequestData(
        ComprehendDocumentReadMode.NO_DATA,
        ComprehendDocumentReadAction.NO_DATA,
        false,
        false,
        "s3://input-bucket/input",
        ComprehendInputFormat.NO_DATA,
        "",
        "arn:aws:iam::123456789012:role/comprehend-data-access-role",
        "arn:aws:comprehend:eu-central-1:123456789012:document-classifier/my-classifier",
        "",
        "",
        "s3://output-bucket/output",
        "",
        Map.of(),
        "",
        List.of(),
        List.of());
  }
}
