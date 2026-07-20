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

import com.amazonaws.ResponseMetadata;
import com.amazonaws.http.SdkHttpMetadata;
import com.amazonaws.services.textract.AmazonTextractAsyncClient;
import com.amazonaws.services.textract.AmazonTextractClient;
import com.amazonaws.services.textract.model.AnalyzeDocumentRequest;
import com.amazonaws.services.textract.model.AnalyzeDocumentResult;
import com.amazonaws.services.textract.model.Block;
import com.amazonaws.services.textract.model.BlockType;
import com.amazonaws.services.textract.model.BoundingBox;
import com.amazonaws.services.textract.model.DocumentMetadata;
import com.amazonaws.services.textract.model.Geometry;
import com.amazonaws.services.textract.model.GetDocumentAnalysisRequest;
import com.amazonaws.services.textract.model.GetDocumentAnalysisResult;
import com.amazonaws.services.textract.model.JobStatus;
import com.amazonaws.services.textract.model.Point;
import com.amazonaws.services.textract.model.Relationship;
import com.amazonaws.services.textract.model.RelationshipType;
import com.amazonaws.services.textract.model.StartDocumentAnalysisResult;
import com.amazonaws.services.textract.model.TextType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.textract.caller.AsyncTextractCaller;
import io.camunda.connector.textract.caller.PollingTextractCaller;
import io.camunda.connector.textract.caller.SyncTextractCaller;
import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Golden-JSON shape tests for the AWS Textract connector result.
 *
 * <p>{@link TextractConnectorFunction} still returns the raw AWS SDK v1 result objects ({@link
 * AnalyzeDocumentResult}, {@link GetDocumentAnalysisResult}, {@link StartDocumentAnalysisResult})
 * directly as the connector's process-variable output; there is no connector-owned result-mapping
 * class (yet). These tests pin the exact JSON that the production {@link ObjectMapper} (see {@link
 * ConnectorsObjectMapperSupplier}) writes for those v1 beans TODAY, including the {@code
 * sdkResponseMetadata} / {@code sdkHttpMetadata} keys that every v1 {@code AmazonWebServiceResult}
 * subclass carries. The upcoming AWS SDK v2 migration must keep this JSON shape green unchanged
 * (see #7968).
 */
class TextractResultShapeTest {

  private final ObjectMapper mapper = ConnectorsObjectMapperSupplier.getCopy();

  /**
   * Serializes {@code value} with the production mapper and re-parses the result into a {@link
   * JsonNode}. Going through text (rather than {@code ObjectMapper#valueToTree}) guarantees
   * floating point bean properties (declared as {@code Float} on the v1 SDK model classes) land in
   * the tree as the same numeric node type ({@code DoubleNode}) that {@link
   * ObjectMapper#readTree(String)} produces for the hand-written expected JSON below; comparing a
   * tree built via {@code valueToTree} (which yields {@code FloatNode}) against one parsed from
   * text (which yields {@code DoubleNode}) would otherwise fail {@code JsonNode#equals} on node
   * type alone, even for numerically identical values.
   */
  private JsonNode treeOf(Object value) throws JsonProcessingException {
    return mapper.readTree(mapper.writeValueAsString(value));
  }

  /** Builds a deterministically ordered header map (insertion order, not {@code Map.of} order). */
  private static Map<String, String> headers(String... keyValuePairs) {
    Map<String, String> result = new LinkedHashMap<>();
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      result.put(keyValuePairs[i], keyValuePairs[i + 1]);
    }
    return result;
  }

  private static Map<String, List<String>> allHeaders(String... keyValuePairs) {
    Map<String, List<String>> result = new LinkedHashMap<>();
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      result.put(keyValuePairs[i], List.of(keyValuePairs[i + 1]));
    }
    return result;
  }

  /**
   * Builds a v1 {@link SdkHttpMetadata} instance. The only public factory ({@code
   * SdkHttpMetadata.from(HttpResponse)}) requires constructing a full {@code
   * com.amazonaws.http.HttpResponse} (which itself needs a live {@code Request} and Apache
   * HttpClient {@code HttpRequestBase}); reflection on the package-private constructor is far
   * simpler and avoids pulling HTTP transport plumbing into a pure shape test.
   *
   * <p>Note (real v1 quirk, not a test artifact): the constructor stores {@code httpHeaders} as-is
   * (preserving whatever order the caller passed), but copies {@code allHttpHeaders} into a plain
   * {@code java.util.HashMap} — so its serialized key order follows {@code String.hashCode()}
   * bucket order, not insertion order. That is deterministic (no randomized seeding, unlike {@code
   * Map.of()}) but not alphabetical or insertion order, which is why the expected JSON below lists
   * {@code allHttpHeaders} keys in a different order than {@code httpHeaders}.
   */
  private static SdkHttpMetadata sdkHttpMetadata(
      Map<String, String> httpHeaders, Map<String, List<String>> allHttpHeaders, int statusCode) {
    try {
      Constructor<SdkHttpMetadata> ctor =
          SdkHttpMetadata.class.getDeclaredConstructor(Map.class, Map.class, int.class);
      ctor.setAccessible(true);
      return ctor.newInstance(httpHeaders, allHttpHeaders, statusCode);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Golden-JSON shape test: a fully populated {@link AnalyzeDocumentResult} (sync execution), with
   * realistic PAGE/LINE/WORD blocks (geometry, bounding box, relationships, confidence) and
   * documentMetadata, as returned by a live AnalyzeDocument call.
   */
  @Test
  void analyzeDocumentResult_serializesToDocumentedV1JsonShape() throws JsonProcessingException {
    // Given a fully populated AnalyzeDocumentResult: one page, one line made of two words, all
    // linked via CHILD relationships, each with geometry/boundingBox and confidence.
    Block wordOne =
        new Block()
            .withId("6cfd6798-0000-0000-0000-000000000003")
            .withBlockType(BlockType.WORD)
            .withConfidence(99.13f)
            .withText("Invoice")
            .withTextType(TextType.PRINTED)
            .withPage(1)
            .withGeometry(
                new Geometry()
                    .withBoundingBox(
                        new BoundingBox()
                            .withWidth(0.12f)
                            .withHeight(0.02f)
                            .withLeft(0.08f)
                            .withTop(0.10f))
                    .withPolygon(
                        List.of(
                            new Point().withX(0.08f).withY(0.10f),
                            new Point().withX(0.20f).withY(0.10f),
                            new Point().withX(0.20f).withY(0.12f),
                            new Point().withX(0.08f).withY(0.12f))));

    Block wordTwo =
        new Block()
            .withId("6cfd6798-0000-0000-0000-000000000004")
            .withBlockType(BlockType.WORD)
            .withConfidence(98.47f)
            .withText("Total:")
            .withTextType(TextType.PRINTED)
            .withPage(1)
            .withGeometry(
                new Geometry()
                    .withBoundingBox(
                        new BoundingBox()
                            .withWidth(0.10f)
                            .withHeight(0.02f)
                            .withLeft(0.21f)
                            .withTop(0.10f))
                    .withPolygon(
                        List.of(
                            new Point().withX(0.21f).withY(0.10f),
                            new Point().withX(0.31f).withY(0.10f),
                            new Point().withX(0.31f).withY(0.12f),
                            new Point().withX(0.21f).withY(0.12f))));

    Block line =
        new Block()
            .withId("6cfd6798-0000-0000-0000-000000000002")
            .withBlockType(BlockType.LINE)
            .withConfidence(99.02f)
            .withText("Invoice Total:")
            .withPage(1)
            .withGeometry(
                new Geometry()
                    .withBoundingBox(
                        new BoundingBox()
                            .withWidth(0.23f)
                            .withHeight(0.02f)
                            .withLeft(0.08f)
                            .withTop(0.10f))
                    .withPolygon(
                        List.of(
                            new Point().withX(0.08f).withY(0.10f),
                            new Point().withX(0.31f).withY(0.10f),
                            new Point().withX(0.31f).withY(0.12f),
                            new Point().withX(0.08f).withY(0.12f))))
            .withRelationships(
                new Relationship()
                    .withType(RelationshipType.CHILD)
                    .withIds(wordOne.getId(), wordTwo.getId()));

    Block page =
        new Block()
            .withId("6cfd6798-0000-0000-0000-000000000001")
            .withBlockType(BlockType.PAGE)
            .withPage(1)
            .withGeometry(
                new Geometry()
                    .withBoundingBox(
                        new BoundingBox()
                            .withWidth(1.0f)
                            .withHeight(1.0f)
                            .withLeft(0.0f)
                            .withTop(0.0f))
                    .withPolygon(
                        List.of(
                            new Point().withX(0.0f).withY(0.0f),
                            new Point().withX(1.0f).withY(0.0f),
                            new Point().withX(1.0f).withY(1.0f),
                            new Point().withX(0.0f).withY(1.0f))))
            .withRelationships(
                new Relationship().withType(RelationshipType.CHILD).withIds(line.getId()));

    AnalyzeDocumentResult result =
        new AnalyzeDocumentResult()
            .withDocumentMetadata(new DocumentMetadata().withPages(1))
            .withBlocks(page, line, wordOne, wordTwo)
            .withAnalyzeDocumentModelVersion("1.0");
    // humanLoopActivationOutput is intentionally left unset (human review was not triggered) to
    // pin that the production mapper still emits it as an explicit null.
    result.setSdkResponseMetadata(
        new ResponseMetadata(Map.of(ResponseMetadata.AWS_REQUEST_ID, "req-sync-0000000001")));
    result.setSdkHttpMetadata(
        sdkHttpMetadata(
            headers(
                "x-amzn-RequestId", "req-sync-0000000001",
                "Content-Type", "application/x-amz-json-1.1",
                "Content-Length", "2048"),
            allHeaders(
                "x-amzn-RequestId", "req-sync-0000000001",
                "Content-Type", "application/x-amz-json-1.1",
                "Content-Length", "2048"),
            200));

    // When the connector caller runs against a mocked client (as in SyncTextractCallerTest) and
    // the runtime serializes the raw v1 result with the production mapper
    AmazonTextractClient textractClient = mock(AmazonTextractClient.class);
    when(textractClient.analyzeDocument(any(AnalyzeDocumentRequest.class))).thenReturn(result);

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
              "x-amzn-RequestId": "req-sync-0000000001",
              "Content-Type": "application/x-amz-json-1.1",
              "Content-Length": "2048"
            },
            "httpStatusCode": 200,
            "allHttpHeaders": {
              "x-amzn-RequestId": ["req-sync-0000000001"],
              "Content-Length": ["2048"],
              "Content-Type": ["application/x-amz-json-1.1"]
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
   * it polls into a single list (via {@code setBlocks}) but returns the LAST {@code
   * GetDocumentAnalysisResult} object as-is otherwise — so the merged result's own {@code
   * sdkResponseMetadata}/{@code sdkHttpMetadata}/{@code nextToken} reflect only the final page
   * call, not the first. This is intentional (if surprising) v1-caller behavior being pinned for
   * the migration.
   */
  @Test
  void getDocumentAnalysisResult_multiPageMerge_serializesToDocumentedV1JsonShape()
      throws Exception {
    String jobId = "5cfa9b10-aaaa-bbbb-cccc-000000000099";

    GetDocumentAnalysisRequest firstRequest =
        new GetDocumentAnalysisRequest().withJobId(jobId).withMaxResults(MAX_RESULT);
    GetDocumentAnalysisRequest secondRequest =
        new GetDocumentAnalysisRequest()
            .withJobId(jobId)
            .withMaxResults(MAX_RESULT)
            .withNextToken("page-2-token");

    Block firstPageBlock =
        new Block()
            .withId("page-1-block")
            .withBlockType(BlockType.PAGE)
            .withPage(1)
            .withGeometry(
                new Geometry()
                    .withBoundingBox(
                        new BoundingBox()
                            .withWidth(1.0f)
                            .withHeight(1.0f)
                            .withLeft(0.0f)
                            .withTop(0.0f)));
    Block firstPageLine =
        new Block()
            .withId("page-1-line")
            .withBlockType(BlockType.LINE)
            .withConfidence(97.31f)
            .withText("Page one line")
            .withPage(1);

    Block secondPageBlock =
        new Block()
            .withId("page-2-block")
            .withBlockType(BlockType.PAGE)
            .withPage(2)
            .withGeometry(
                new Geometry()
                    .withBoundingBox(
                        new BoundingBox()
                            .withWidth(1.0f)
                            .withHeight(1.0f)
                            .withLeft(0.0f)
                            .withTop(0.0f)));
    Block secondPageLine =
        new Block()
            .withId("page-2-line")
            .withBlockType(BlockType.LINE)
            .withConfidence(96.84f)
            .withText("Page two line")
            .withPage(2);

    GetDocumentAnalysisResult firstPageResult =
        new GetDocumentAnalysisResult()
            .withDocumentMetadata(new DocumentMetadata().withPages(2))
            .withJobStatus(JobStatus.SUCCEEDED.toString())
            .withNextToken("page-2-token")
            .withBlocks(firstPageBlock, firstPageLine)
            .withAnalyzeDocumentModelVersion("1.0");
    firstPageResult.setSdkResponseMetadata(
        new ResponseMetadata(Map.of(ResponseMetadata.AWS_REQUEST_ID, "req-page-0000000001")));
    firstPageResult.setSdkHttpMetadata(
        sdkHttpMetadata(
            headers("x-amzn-RequestId", "req-page-0000000001"),
            allHeaders("x-amzn-RequestId", "req-page-0000000001"),
            200));

    GetDocumentAnalysisResult secondPageResult =
        new GetDocumentAnalysisResult()
            .withDocumentMetadata(new DocumentMetadata().withPages(2))
            .withJobStatus(JobStatus.SUCCEEDED.toString())
            .withNextToken(null)
            .withBlocks(secondPageBlock, secondPageLine)
            .withAnalyzeDocumentModelVersion("1.0");
    secondPageResult.setSdkResponseMetadata(
        new ResponseMetadata(Map.of(ResponseMetadata.AWS_REQUEST_ID, "req-page-0000000002")));
    secondPageResult.setSdkHttpMetadata(
        sdkHttpMetadata(
            headers("x-amzn-RequestId", "req-page-0000000002"),
            allHeaders("x-amzn-RequestId", "req-page-0000000002"),
            200));

    AmazonTextractAsyncClient asyncClient = mock(AmazonTextractAsyncClient.class);
    when(asyncClient.startDocumentAnalysis(any()))
        .thenReturn(new StartDocumentAnalysisResult().withJobId(jobId));
    when(asyncClient.getDocumentAnalysis(firstRequest)).thenReturn(firstPageResult);
    when(asyncClient.getDocumentAnalysis(secondRequest)).thenReturn(secondPageResult);

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
   * Golden-JSON shape test: a fully populated {@link StartDocumentAnalysisResult} (async start).
   */
  @Test
  void startDocumentAnalysisResult_serializesToDocumentedV1JsonShape()
      throws JsonProcessingException {
    StartDocumentAnalysisResult result =
        new StartDocumentAnalysisResult().withJobId("649bf054-193b-48e6-ab80-3aeeb613b415");
    result.setSdkResponseMetadata(
        new ResponseMetadata(Map.of(ResponseMetadata.AWS_REQUEST_ID, "req-async-0000000001")));
    result.setSdkHttpMetadata(
        sdkHttpMetadata(
            headers(
                "x-amzn-RequestId", "req-async-0000000001",
                "Content-Type", "application/x-amz-json-1.1"),
            allHeaders(
                "x-amzn-RequestId", "req-async-0000000001",
                "Content-Type", "application/x-amz-json-1.1"),
            200));

    AmazonTextractAsyncClient asyncClient = mock(AmazonTextractAsyncClient.class);
    when(asyncClient.startDocumentAnalysis(any())).thenReturn(result);

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
              "x-amzn-RequestId": "req-async-0000000001",
              "Content-Type": "application/x-amz-json-1.1"
            },
            "httpStatusCode": 200,
            "allHttpHeaders": {
              "x-amzn-RequestId": ["req-async-0000000001"],
              "Content-Type": ["application/x-amz-json-1.1"]
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
