/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract;

import static io.camunda.connector.textract.caller.PollingTextractCaller.MAX_RESULT;
import static io.camunda.connector.textract.util.TextractTestUtils.FULL_FILLED_ASYNC_TEXTRACT_DATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.textract.caller.AsyncTextractCaller;
import io.camunda.connector.textract.caller.PollingTextractCaller;
import io.camunda.connector.textract.caller.SyncTextractCaller;
import io.camunda.connector.textract.model.result.AnalyzeDocumentResult;
import io.camunda.connector.textract.model.result.GetDocumentAnalysisResult;
import io.camunda.connector.textract.model.result.StartDocumentAnalysisResult;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.DefaultAwsResponseMetadata;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.textract.TextractAsyncClient;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.AnalyzeDocumentRequest;
import software.amazon.awssdk.services.textract.model.AnalyzeDocumentResponse;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.BoundingBox;
import software.amazon.awssdk.services.textract.model.DocumentMetadata;
import software.amazon.awssdk.services.textract.model.Geometry;
import software.amazon.awssdk.services.textract.model.GetDocumentAnalysisRequest;
import software.amazon.awssdk.services.textract.model.GetDocumentAnalysisResponse;
import software.amazon.awssdk.services.textract.model.JobStatus;
import software.amazon.awssdk.services.textract.model.Point;
import software.amazon.awssdk.services.textract.model.Relationship;
import software.amazon.awssdk.services.textract.model.RelationshipType;
import software.amazon.awssdk.services.textract.model.StartDocumentAnalysisRequest;
import software.amazon.awssdk.services.textract.model.StartDocumentAnalysisResponse;
import software.amazon.awssdk.services.textract.model.TextType;

/**
 * Golden-JSON shape tests for the AWS Textract connector result.
 *
 * <p>These tests pin the exact JSON that the production {@link ObjectMapper} (see {@link
 * ConnectorsObjectMapperSupplier}) writes for the connector-owned result records ({@link
 * AnalyzeDocumentResult}, {@link StartDocumentAnalysisResult}, {@link GetDocumentAnalysisResult}),
 * built from AWS SDK v2 responses. This is the exact JSON shape the connector documented and
 * returned before the AWS SDK v2 migration (see #7968) - the migration must keep it unchanged.
 */
class TextractResultShapeTest {

  private final ObjectMapper mapper = ConnectorsObjectMapperSupplier.getCopy();

  private JsonNode treeOf(Object value) throws JsonProcessingException {
    return mapper.readTree(mapper.writeValueAsString(value));
  }

  private static SdkHttpResponse sdkHttpResponse(int statusCode, String... headerKeyValuePairs) {
    SdkHttpResponse.Builder builder = SdkHttpResponse.builder().statusCode(statusCode);
    for (int i = 0; i < headerKeyValuePairs.length; i += 2) {
      builder.putHeader(headerKeyValuePairs[i], headerKeyValuePairs[i + 1]);
    }
    return builder.build();
  }

  /**
   * Golden-JSON shape test: a fully populated {@link AnalyzeDocumentResponse} (sync execution),
   * with realistic PAGE/LINE/WORD blocks (geometry, bounding box, relationships, confidence) and
   * documentMetadata, as returned by a live AnalyzeDocument call.
   */
  @Test
  void analyzeDocumentResult_serializesToDocumentedV1JsonShape() throws JsonProcessingException {
    // Given a fully populated AnalyzeDocumentResponse: one page, one line made of two words, all
    // linked via CHILD relationships, each with geometry/boundingBox and confidence.
    software.amazon.awssdk.services.textract.model.Block wordOne =
        software.amazon.awssdk.services.textract.model.Block.builder()
            .id("6cfd6798-0000-0000-0000-000000000003")
            .blockType(BlockType.WORD)
            .confidence(99.13f)
            .text("Invoice")
            .textType(TextType.PRINTED)
            .page(1)
            .geometry(
                Geometry.builder()
                    .boundingBox(
                        BoundingBox.builder()
                            .width(0.12f)
                            .height(0.02f)
                            .left(0.08f)
                            .top(0.10f)
                            .build())
                    .polygon(
                        Point.builder().x(0.08f).y(0.10f).build(),
                        Point.builder().x(0.20f).y(0.10f).build(),
                        Point.builder().x(0.20f).y(0.12f).build(),
                        Point.builder().x(0.08f).y(0.12f).build())
                    .build())
            .build();

    software.amazon.awssdk.services.textract.model.Block wordTwo =
        software.amazon.awssdk.services.textract.model.Block.builder()
            .id("6cfd6798-0000-0000-0000-000000000004")
            .blockType(BlockType.WORD)
            .confidence(98.47f)
            .text("Total:")
            .textType(TextType.PRINTED)
            .page(1)
            .geometry(
                Geometry.builder()
                    .boundingBox(
                        BoundingBox.builder()
                            .width(0.10f)
                            .height(0.02f)
                            .left(0.21f)
                            .top(0.10f)
                            .build())
                    .polygon(
                        Point.builder().x(0.21f).y(0.10f).build(),
                        Point.builder().x(0.31f).y(0.10f).build(),
                        Point.builder().x(0.31f).y(0.12f).build(),
                        Point.builder().x(0.21f).y(0.12f).build())
                    .build())
            .build();

    software.amazon.awssdk.services.textract.model.Block line =
        software.amazon.awssdk.services.textract.model.Block.builder()
            .id("6cfd6798-0000-0000-0000-000000000002")
            .blockType(BlockType.LINE)
            .confidence(99.02f)
            .text("Invoice Total:")
            .page(1)
            .geometry(
                Geometry.builder()
                    .boundingBox(
                        BoundingBox.builder()
                            .width(0.23f)
                            .height(0.02f)
                            .left(0.08f)
                            .top(0.10f)
                            .build())
                    .polygon(
                        Point.builder().x(0.08f).y(0.10f).build(),
                        Point.builder().x(0.31f).y(0.10f).build(),
                        Point.builder().x(0.31f).y(0.12f).build(),
                        Point.builder().x(0.08f).y(0.12f).build())
                    .build())
            .relationships(
                Relationship.builder()
                    .type(RelationshipType.CHILD)
                    .ids(wordOne.id(), wordTwo.id())
                    .build())
            .build();

    software.amazon.awssdk.services.textract.model.Block page =
        software.amazon.awssdk.services.textract.model.Block.builder()
            .id("6cfd6798-0000-0000-0000-000000000001")
            .blockType(BlockType.PAGE)
            .page(1)
            .geometry(
                Geometry.builder()
                    .boundingBox(
                        BoundingBox.builder().width(1.0f).height(1.0f).left(0.0f).top(0.0f).build())
                    .polygon(
                        Point.builder().x(0.0f).y(0.0f).build(),
                        Point.builder().x(1.0f).y(0.0f).build(),
                        Point.builder().x(1.0f).y(1.0f).build(),
                        Point.builder().x(0.0f).y(1.0f).build())
                    .build())
            .relationships(
                Relationship.builder().type(RelationshipType.CHILD).ids(line.id()).build())
            .build();

    AnalyzeDocumentResponse.Builder responseBuilder =
        AnalyzeDocumentResponse.builder()
            .documentMetadata(DocumentMetadata.builder().pages(1).build())
            .blocks(page, line, wordOne, wordTwo)
            .analyzeDocumentModelVersion("1.0");
    // humanLoopActivationOutput is intentionally left unset (human review was not triggered) to
    // pin that the production mapper still emits it as an explicit null.
    responseBuilder.responseMetadata(
        DefaultAwsResponseMetadata.create(Map.of("AWS_REQUEST_ID", "req-sync-0000000001")));
    responseBuilder.sdkHttpResponse(
        sdkHttpResponse(
            200,
            "x-amzn-RequestId",
            "req-sync-0000000001",
            "Content-Type",
            "application/x-amz-json-1.1",
            "Content-Length",
            "2048"));
    AnalyzeDocumentResponse response = responseBuilder.build();

    // When the connector caller runs against a mocked client (as in SyncTextractCallerTest) and
    // the runtime serializes the connector-owned result with the production mapper
    TextractClient textractClient = mock(TextractClient.class);
    when(textractClient.analyzeDocument(any(AnalyzeDocumentRequest.class))).thenReturn(response);

    AnalyzeDocumentResult actualResult =
        new SyncTextractCaller().call(FULL_FILLED_ASYNC_TEXTRACT_DATA, textractClient);
    JsonNode actual = treeOf(actualResult);

    // Then the JSON matches the documented v1 output shape exactly, including explicit nulls.
    String expectedJson =
        """
        {
          "sdkResponseMetadata": {
            "requestId": "req-sync-0000000001"
          },
          "sdkHttpMetadata": {
            "httpHeaders": {
              "Content-Length": "2048",
              "Content-Type": "application/x-amz-json-1.1",
              "x-amzn-RequestId": "req-sync-0000000001"
            },
            "httpStatusCode": 200,
            "allHttpHeaders": {
              "Content-Length": ["2048"],
              "Content-Type": ["application/x-amz-json-1.1"],
              "x-amzn-RequestId": ["req-sync-0000000001"]
            }
          },
          "documentMetadata": {
            "pages": 1
          },
          "blocks": [
            {
              "blockType": "PAGE",
              "confidence": null,
              "text": null,
              "textType": null,
              "rowIndex": null,
              "columnIndex": null,
              "rowSpan": null,
              "columnSpan": null,
              "geometry": {
                "boundingBox": { "width": 1.0, "height": 1.0, "left": 0.0, "top": 0.0 },
                "polygon": [
                  { "x": 0.0, "y": 0.0 },
                  { "x": 1.0, "y": 0.0 },
                  { "x": 1.0, "y": 1.0 },
                  { "x": 0.0, "y": 1.0 }
                ]
              },
              "id": "6cfd6798-0000-0000-0000-000000000001",
              "relationships": [
                { "type": "CHILD", "ids": ["6cfd6798-0000-0000-0000-000000000002"] }
              ],
              "entityTypes": null,
              "selectionStatus": null,
              "page": 1,
              "query": null
            },
            {
              "blockType": "LINE",
              "confidence": 99.02,
              "text": "Invoice Total:",
              "textType": null,
              "rowIndex": null,
              "columnIndex": null,
              "rowSpan": null,
              "columnSpan": null,
              "geometry": {
                "boundingBox": { "width": 0.23, "height": 0.02, "left": 0.08, "top": 0.1 },
                "polygon": [
                  { "x": 0.08, "y": 0.1 },
                  { "x": 0.31, "y": 0.1 },
                  { "x": 0.31, "y": 0.12 },
                  { "x": 0.08, "y": 0.12 }
                ]
              },
              "id": "6cfd6798-0000-0000-0000-000000000002",
              "relationships": [
                {
                  "type": "CHILD",
                  "ids": [
                    "6cfd6798-0000-0000-0000-000000000003",
                    "6cfd6798-0000-0000-0000-000000000004"
                  ]
                }
              ],
              "entityTypes": null,
              "selectionStatus": null,
              "page": 1,
              "query": null
            },
            {
              "blockType": "WORD",
              "confidence": 99.13,
              "text": "Invoice",
              "textType": "PRINTED",
              "rowIndex": null,
              "columnIndex": null,
              "rowSpan": null,
              "columnSpan": null,
              "geometry": {
                "boundingBox": { "width": 0.12, "height": 0.02, "left": 0.08, "top": 0.1 },
                "polygon": [
                  { "x": 0.08, "y": 0.1 },
                  { "x": 0.2, "y": 0.1 },
                  { "x": 0.2, "y": 0.12 },
                  { "x": 0.08, "y": 0.12 }
                ]
              },
              "id": "6cfd6798-0000-0000-0000-000000000003",
              "relationships": null,
              "entityTypes": null,
              "selectionStatus": null,
              "page": 1,
              "query": null
            },
            {
              "blockType": "WORD",
              "confidence": 98.47,
              "text": "Total:",
              "textType": "PRINTED",
              "rowIndex": null,
              "columnIndex": null,
              "rowSpan": null,
              "columnSpan": null,
              "geometry": {
                "boundingBox": { "width": 0.1, "height": 0.02, "left": 0.21, "top": 0.1 },
                "polygon": [
                  { "x": 0.21, "y": 0.1 },
                  { "x": 0.31, "y": 0.1 },
                  { "x": 0.31, "y": 0.12 },
                  { "x": 0.21, "y": 0.12 }
                ]
              },
              "id": "6cfd6798-0000-0000-0000-000000000004",
              "relationships": null,
              "entityTypes": null,
              "selectionStatus": null,
              "page": 1,
              "query": null
            }
          ],
          "humanLoopActivationOutput": null,
          "analyzeDocumentModelVersion": "1.0"
        }
        """;
    JsonNode expected = mapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);
    // Tree equality above is key-order-insensitive; also pin the serialized field order to the
    // documented v1 layout.
    assertThat(mapper.writeValueAsString(actualResult))
        .isEqualTo(mapper.writeValueAsString(expected));
  }

  /**
   * Golden-JSON shape test: {@link PollingTextractCaller} merges the {@code blocks} of every page
   * it polls into a single list (via {@code toBuilder().blocks(...)}, since v2 responses are
   * immutable) but returns the LAST {@link GetDocumentAnalysisResponse} otherwise - so the merged
   * result's own {@code sdkResponseMetadata}/{@code sdkHttpMetadata}/{@code nextToken} reflect only
   * the final page call, not the first. This is intentional (if surprising) caller behavior being
   * pinned across the migration.
   */
  @Test
  void getDocumentAnalysisResult_multiPageMerge_serializesToDocumentedV1JsonShape()
      throws Exception {
    String jobId = "5cfa9b10-aaaa-bbbb-cccc-000000000099";

    GetDocumentAnalysisRequest firstRequest =
        GetDocumentAnalysisRequest.builder().jobId(jobId).maxResults(MAX_RESULT).build();
    GetDocumentAnalysisRequest secondRequest =
        GetDocumentAnalysisRequest.builder()
            .jobId(jobId)
            .maxResults(MAX_RESULT)
            .nextToken("page-2-token")
            .build();

    software.amazon.awssdk.services.textract.model.Block firstPageBlock =
        software.amazon.awssdk.services.textract.model.Block.builder()
            .id("page-1-block")
            .blockType(BlockType.PAGE)
            .page(1)
            .geometry(
                Geometry.builder()
                    .boundingBox(
                        BoundingBox.builder().width(1.0f).height(1.0f).left(0.0f).top(0.0f).build())
                    .build())
            .build();
    software.amazon.awssdk.services.textract.model.Block firstPageLine =
        software.amazon.awssdk.services.textract.model.Block.builder()
            .id("page-1-line")
            .blockType(BlockType.LINE)
            .confidence(97.31f)
            .text("Page one line")
            .page(1)
            .build();

    software.amazon.awssdk.services.textract.model.Block secondPageBlock =
        software.amazon.awssdk.services.textract.model.Block.builder()
            .id("page-2-block")
            .blockType(BlockType.PAGE)
            .page(2)
            .geometry(
                Geometry.builder()
                    .boundingBox(
                        BoundingBox.builder().width(1.0f).height(1.0f).left(0.0f).top(0.0f).build())
                    .build())
            .build();
    software.amazon.awssdk.services.textract.model.Block secondPageLine =
        software.amazon.awssdk.services.textract.model.Block.builder()
            .id("page-2-line")
            .blockType(BlockType.LINE)
            .confidence(96.84f)
            .text("Page two line")
            .page(2)
            .build();

    GetDocumentAnalysisResponse.Builder firstPageBuilder =
        GetDocumentAnalysisResponse.builder()
            .documentMetadata(DocumentMetadata.builder().pages(2).build())
            .jobStatus(JobStatus.SUCCEEDED)
            .nextToken("page-2-token")
            .blocks(firstPageBlock, firstPageLine)
            .analyzeDocumentModelVersion("1.0");
    firstPageBuilder.responseMetadata(
        DefaultAwsResponseMetadata.create(Map.of("AWS_REQUEST_ID", "req-page-0000000001")));
    firstPageBuilder.sdkHttpResponse(
        sdkHttpResponse(200, "x-amzn-RequestId", "req-page-0000000001"));
    GetDocumentAnalysisResponse firstPageResult = firstPageBuilder.build();

    GetDocumentAnalysisResponse.Builder secondPageBuilder =
        GetDocumentAnalysisResponse.builder()
            .documentMetadata(DocumentMetadata.builder().pages(2).build())
            .jobStatus(JobStatus.SUCCEEDED)
            .blocks(secondPageBlock, secondPageLine)
            .analyzeDocumentModelVersion("1.0");
    secondPageBuilder.responseMetadata(
        DefaultAwsResponseMetadata.create(Map.of("AWS_REQUEST_ID", "req-page-0000000002")));
    secondPageBuilder.sdkHttpResponse(
        sdkHttpResponse(200, "x-amzn-RequestId", "req-page-0000000002"));
    GetDocumentAnalysisResponse secondPageResult = secondPageBuilder.build();

    TextractAsyncClient asyncClient = mock(TextractAsyncClient.class);
    when(asyncClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                StartDocumentAnalysisResponse.builder().jobId(jobId).build()));
    when(asyncClient.getDocumentAnalysis(firstRequest))
        .thenReturn(CompletableFuture.completedFuture(firstPageResult));
    when(asyncClient.getDocumentAnalysis(secondRequest))
        .thenReturn(CompletableFuture.completedFuture(secondPageResult));

    GetDocumentAnalysisResult mergedResult =
        new PollingTextractCaller().call(FULL_FILLED_ASYNC_TEXTRACT_DATA, asyncClient);
    JsonNode actual = treeOf(mergedResult);

    String expectedJson =
        """
        {
          "sdkResponseMetadata": {
            "requestId": "req-page-0000000002"
          },
          "sdkHttpMetadata": {
            "httpHeaders": {
              "x-amzn-RequestId": "req-page-0000000002"
            },
            "httpStatusCode": 200,
            "allHttpHeaders": {
              "x-amzn-RequestId": ["req-page-0000000002"]
            }
          },
          "documentMetadata": {
            "pages": 2
          },
          "jobStatus": "SUCCEEDED",
          "nextToken": null,
          "blocks": [
            {
              "blockType": "PAGE",
              "confidence": null,
              "text": null,
              "textType": null,
              "rowIndex": null,
              "columnIndex": null,
              "rowSpan": null,
              "columnSpan": null,
              "geometry": {
                "boundingBox": { "width": 1.0, "height": 1.0, "left": 0.0, "top": 0.0 },
                "polygon": null
              },
              "id": "page-1-block",
              "relationships": null,
              "entityTypes": null,
              "selectionStatus": null,
              "page": 1,
              "query": null
            },
            {
              "blockType": "LINE",
              "confidence": 97.31,
              "text": "Page one line",
              "textType": null,
              "rowIndex": null,
              "columnIndex": null,
              "rowSpan": null,
              "columnSpan": null,
              "geometry": null,
              "id": "page-1-line",
              "relationships": null,
              "entityTypes": null,
              "selectionStatus": null,
              "page": 1,
              "query": null
            },
            {
              "blockType": "PAGE",
              "confidence": null,
              "text": null,
              "textType": null,
              "rowIndex": null,
              "columnIndex": null,
              "rowSpan": null,
              "columnSpan": null,
              "geometry": {
                "boundingBox": { "width": 1.0, "height": 1.0, "left": 0.0, "top": 0.0 },
                "polygon": null
              },
              "id": "page-2-block",
              "relationships": null,
              "entityTypes": null,
              "selectionStatus": null,
              "page": 2,
              "query": null
            },
            {
              "blockType": "LINE",
              "confidence": 96.84,
              "text": "Page two line",
              "textType": null,
              "rowIndex": null,
              "columnIndex": null,
              "rowSpan": null,
              "columnSpan": null,
              "geometry": null,
              "id": "page-2-line",
              "relationships": null,
              "entityTypes": null,
              "selectionStatus": null,
              "page": 2,
              "query": null
            }
          ],
          "warnings": null,
          "statusMessage": null,
          "analyzeDocumentModelVersion": "1.0"
        }
        """;
    JsonNode expected = mapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);
    assertThat(mapper.writeValueAsString(mergedResult))
        .isEqualTo(mapper.writeValueAsString(expected));
  }

  /**
   * Golden-JSON shape test: a fully populated {@link StartDocumentAnalysisResponse} (async start).
   */
  @Test
  void startDocumentAnalysisResult_serializesToDocumentedV1JsonShape()
      throws JsonProcessingException {
    StartDocumentAnalysisResponse.Builder responseBuilder =
        StartDocumentAnalysisResponse.builder().jobId("649bf054-193b-48e6-ab80-3aeeb613b415");
    responseBuilder.responseMetadata(
        DefaultAwsResponseMetadata.create(Map.of("AWS_REQUEST_ID", "req-async-0000000001")));
    responseBuilder.sdkHttpResponse(
        sdkHttpResponse(
            200,
            "x-amzn-RequestId",
            "req-async-0000000001",
            "Content-Type",
            "application/x-amz-json-1.1"));
    StartDocumentAnalysisResponse response = responseBuilder.build();

    TextractAsyncClient asyncClient = mock(TextractAsyncClient.class);
    when(asyncClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(response));

    StartDocumentAnalysisResult actualResult =
        new AsyncTextractCaller().call(FULL_FILLED_ASYNC_TEXTRACT_DATA, asyncClient);
    JsonNode actual = treeOf(actualResult);

    String expectedJson =
        """
        {
          "sdkResponseMetadata": {
            "requestId": "req-async-0000000001"
          },
          "sdkHttpMetadata": {
            "httpHeaders": {
              "Content-Type": "application/x-amz-json-1.1",
              "x-amzn-RequestId": "req-async-0000000001"
            },
            "httpStatusCode": 200,
            "allHttpHeaders": {
              "Content-Type": ["application/x-amz-json-1.1"],
              "x-amzn-RequestId": ["req-async-0000000001"]
            }
          },
          "jobId": "649bf054-193b-48e6-ab80-3aeeb613b415"
        }
        """;
    JsonNode expected = mapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);
    assertThat(mapper.writeValueAsString(actualResult))
        .isEqualTo(mapper.writeValueAsString(expected));
  }
}
