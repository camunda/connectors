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

import com.amazonaws.ResponseMetadata;
import com.amazonaws.http.SdkHttpMetadata;
import com.amazonaws.services.comprehend.AmazonComprehendAsyncClient;
import com.amazonaws.services.comprehend.AmazonComprehendClient;
import com.amazonaws.services.comprehend.model.ClassifyDocumentRequest;
import com.amazonaws.services.comprehend.model.ClassifyDocumentResult;
import com.amazonaws.services.comprehend.model.DocumentClass;
import com.amazonaws.services.comprehend.model.DocumentMetadata;
import com.amazonaws.services.comprehend.model.DocumentTypeListItem;
import com.amazonaws.services.comprehend.model.ExtractedCharactersListItem;
import com.amazonaws.services.comprehend.model.StartDocumentClassificationJobRequest;
import com.amazonaws.services.comprehend.model.StartDocumentClassificationJobResult;
import com.amazonaws.services.comprehend.model.WarningsListItem;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Golden-JSON shape tests for the AWS Comprehend connector's outbound result.
 *
 * <p>{@link ComprehendConnectorFunction} returns the raw AWS SDK v1 result objects ({@link
 * ClassifyDocumentResult}, {@link StartDocumentClassificationJobResult}) directly as the process
 * variable payload -- there is no connector-owned result-mapping class. Both are {@code
 * AmazonWebServiceResult} subclasses that expose plain JavaBean getters (unlike the AWS SDK v2
 * model classes, which use fluent accessors), so they serialize fine today under the production
 * {@code ObjectMapper} (which disables {@code FAIL_ON_EMPTY_BEANS}). These tests pin that exact v1
 * JSON shape -- including the {@code sdkResponseMetadata}/{@code sdkHttpMetadata} keys inherited
 * from {@code AmazonWebServiceResult} and all explicit nulls -- as the contract the upcoming AWS
 * SDK v2 migration must reproduce unchanged.
 */
class ComprehendResultJsonShapeTest {

  private final ObjectMapper productionMapper = ConnectorsObjectMapperSupplier.getCopy();
  private final SyncComprehendCaller syncCaller = new SyncComprehendCaller();
  private final AsyncComprehendCaller asyncCaller = new AsyncComprehendCaller();

  /**
   * Sync classify: a fully populated {@link ClassifyDocumentResult} as returned by a live,
   * multi-page {@code ClassifyDocument} call against a TEXTRACT-backed custom classifier -- classes
   * (name/score/page), document metadata, document type, warnings, an explicitly empty errors list,
   * and populated SDK response/HTTP metadata.
   */
  @Test
  void classifyDocumentResult_populated_serializesToDocumentedV1JsonShape()
      throws JsonProcessingException {
    // Given a fully populated ClassifyDocumentResult, as returned by a live ClassifyDocument call
    var result =
        new ClassifyDocumentResult()
            .withClasses(
                new DocumentClass().withName("POSITIVE").withScore(0.987654f).withPage(1),
                new DocumentClass().withName("NEGATIVE").withScore(0.012346f).withPage(1))
            .withDocumentMetadata(
                new DocumentMetadata()
                    .withPages(2)
                    .withExtractedCharacters(
                        new ExtractedCharactersListItem().withPage(1).withCount(1200),
                        new ExtractedCharactersListItem().withPage(2).withCount(980)))
            .withDocumentType(new DocumentTypeListItem().withPage(1).withType("NATIVE_PDF"))
            .withErrors()
            .withWarnings(
                new WarningsListItem()
                    .withPage(2)
                    .withWarnCode("INFERENCING_PLAINTEXT_WITH_NATIVE_TRAINED_MODEL")
                    .withWarnMessage(
                        "The document was submitted as plain text but the model was trained on"
                            + " native documents."));
    result.setSdkResponseMetadata(
        new ResponseMetadata(Map.of(ResponseMetadata.AWS_REQUEST_ID, REQUEST_ID)));
    result.setSdkHttpMetadata(populatedHttpMetadata());

    AmazonComprehendClient client = mock(AmazonComprehendClient.class);
    when(client.classifyDocument(any(ClassifyDocumentRequest.class))).thenReturn(result);

    // When the caller returns the client's response, unmodified, as the connector does today
    ClassifyDocumentResult actualResult =
        syncCaller.call(client, new ComprehendSyncRequestData("plain text", "endpoint-arn"));

    // Then the JSON produced by the production mapper matches the documented v1 shape exactly,
    // including explicit nulls (labels is null: a multi-class classifier response never populates
    // the multi-label "labels" field).
    // Parsed from the mapper's own serialized string (not valueToTree) so that numeric node types
    // match the expected side exactly -- valueToTree would keep the Float DocumentClass#score as
    // a FloatNode, which JsonNode#equals treats as unequal to the DoubleNode produced by parsing
    // the JSON text below, even when the numeric value is identical.
    JsonNode actual = productionMapper.readTree(productionMapper.writeValueAsString(actualResult));
    // Property order below is NOT alphabetical or declaration order: it is whatever the
    // production mapper's default bean introspection produces for this class today (the
    // AmazonWebServiceResult-inherited sdkResponseMetadata/sdkHttpMetadata surface first, ahead of
    // this class's own fields, since there is no @JsonPropertyOrder on the AWS SDK v1 model
    // classes). This is intentional v1 behavior being pinned for the migration.
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
    // one the production mapper produces today (default bean-introspection order; there is no
    // @JsonPropertyOrder on the AWS SDK v1 model classes).
    assertThat(productionMapper.writeValueAsString(actualResult))
        .isEqualTo(productionMapper.writeValueAsString(expected));
  }

  /**
   * Sync classify: the empty/minimal {@link ClassifyDocumentResult} a mocked (or genuinely
   * near-empty) response yields -- every field, including the inherited SDK metadata, is null. This
   * is also exactly what {@code new ClassifyDocumentResult()} produces, as already exercised by
   * {@code SyncComprehendCallerTest} and {@code ComprehendConnectorFunctionTest}.
   */
  @Test
  void classifyDocumentResult_empty_serializesToDocumentedV1JsonShape()
      throws JsonProcessingException {
    // Given a minimal/empty ClassifyDocumentResult (no field populated)
    var result = new ClassifyDocumentResult();

    AmazonComprehendClient client = mock(AmazonComprehendClient.class);
    when(client.classifyDocument(any(ClassifyDocumentRequest.class))).thenReturn(result);

    // When the caller returns the client's response, unmodified, as the connector does today
    ClassifyDocumentResult actualResult =
        syncCaller.call(client, new ComprehendSyncRequestData("plain text", "endpoint-arn"));

    // Then every field -- including the two AmazonWebServiceResult-inherited SDK metadata keys --
    // is serialized as an explicit null (the production mapper disables FAIL_ON_EMPTY_BEANS, so
    // this does not collapse to "{}").
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
   * Async job-start: a populated {@link StartDocumentClassificationJobResult} as returned by a live
   * {@code StartDocumentClassificationJob} call (job accepted and submitted).
   */
  @Test
  void startDocumentClassificationJobResult_serializesToDocumentedV1JsonShape()
      throws JsonProcessingException {
    // Given a populated StartDocumentClassificationJobResult, as returned by a live
    // StartDocumentClassificationJob call
    var result =
        new StartDocumentClassificationJobResult()
            .withJobId("12345678-1234-1234-1234-123456789012")
            .withJobArn(
                "arn:aws:comprehend:eu-central-1:123456789012:document-classification-job/12345678-1234-1234-1234-123456789012")
            .withJobStatus("SUBMITTED")
            .withDocumentClassifierArn(
                "arn:aws:comprehend:eu-central-1:123456789012:document-classifier/my-classifier");
    result.setSdkResponseMetadata(
        new ResponseMetadata(Map.of(ResponseMetadata.AWS_REQUEST_ID, REQUEST_ID)));

    AmazonComprehendAsyncClient client = mock(AmazonComprehendAsyncClient.class);
    when(client.startDocumentClassificationJob(any(StartDocumentClassificationJobRequest.class)))
        .thenReturn(result);

    // When the caller returns the client's response, unmodified, as the connector does today
    StartDocumentClassificationJobResult actualResult =
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

  /**
   * Builds the {@link SdkHttpMetadata} a real ClassifyDocument call's HTTP response carries.
   *
   * <p>{@code SdkHttpMetadata} only exposes a private constructor plus the static {@code
   * from(HttpResponse)} factory, and building a real {@code HttpResponse} would drag in {@code
   * org.apache.httpcomponents:httpclient} as a new direct test dependency just to construct a data
   * holder. A stubbed mock is equivalent from the production mapper's point of view: it only ever
   * calls the three getters below via bean introspection.
   */
  private static SdkHttpMetadata populatedHttpMetadata() {
    Map<String, String> httpHeaders = new LinkedHashMap<>();
    httpHeaders.put("Content-Length", "85");
    httpHeaders.put("Content-Type", "application/x-amz-json-1.1");
    httpHeaders.put("x-amzn-RequestId", REQUEST_ID);
    Map<String, List<String>> allHttpHeaders = new LinkedHashMap<>();
    allHttpHeaders.put("Content-Length", List.of("85"));
    allHttpHeaders.put("Content-Type", List.of("application/x-amz-json-1.1"));
    allHttpHeaders.put("x-amzn-RequestId", List.of(REQUEST_ID));

    SdkHttpMetadata httpMetadata = mock(SdkHttpMetadata.class);
    when(httpMetadata.getHttpHeaders()).thenReturn(httpHeaders);
    when(httpMetadata.getAllHttpHeaders()).thenReturn(allHttpHeaders);
    when(httpMetadata.getHttpStatusCode()).thenReturn(200);
    return httpMetadata;
  }

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
