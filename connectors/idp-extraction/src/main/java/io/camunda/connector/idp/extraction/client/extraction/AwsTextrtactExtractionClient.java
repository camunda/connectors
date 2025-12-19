/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.client.extraction;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.client.extraction.base.MlExtractor;
import io.camunda.connector.idp.extraction.client.extraction.base.TextExtractor;
import io.camunda.connector.idp.extraction.model.Polygon;
import io.camunda.connector.idp.extraction.model.PolygonPoint;
import io.camunda.connector.idp.extraction.model.StructuredExtractionResponse;
import io.camunda.connector.idp.extraction.model.TextractAnalysisTask;
import io.camunda.connector.idp.extraction.model.TextractTextDetectionTask;
import io.camunda.connector.idp.extraction.utils.AwsUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.EntityType;
import software.amazon.awssdk.services.textract.model.FeatureType;
import software.amazon.awssdk.services.textract.model.GetDocumentAnalysisRequest;
import software.amazon.awssdk.services.textract.model.GetDocumentAnalysisResponse;
import software.amazon.awssdk.services.textract.model.GetDocumentTextDetectionRequest;
import software.amazon.awssdk.services.textract.model.GetDocumentTextDetectionResponse;
import software.amazon.awssdk.services.textract.model.JobStatus;
import software.amazon.awssdk.services.textract.model.Point;
import software.amazon.awssdk.services.textract.model.RelationshipType;
import software.amazon.awssdk.services.textract.model.S3Object;
import software.amazon.awssdk.services.textract.model.SelectionStatus;
import software.amazon.awssdk.services.textract.model.StartDocumentAnalysisRequest;
import software.amazon.awssdk.services.textract.model.StartDocumentTextDetectionRequest;

public class AwsTextrtactExtractionClient implements TextExtractor, MlExtractor, AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(AwsTextrtactExtractionClient.class);
  private static final long DELAY_BETWEEN_POLLING = 5;
  private static final int MAX_RESULT = 1000;
  private final TextractClient textractClient;
  private final S3AsyncClient s3AsyncClient;
  private final String bucketName;

  public AwsTextrtactExtractionClient(
      AwsCredentialsProvider credentialsProvider, String region, String bucketName) {
    textractClient =
        TextractClient.builder()
            .credentialsProvider(credentialsProvider)
            .region(Region.of(region))
            .build();
    s3AsyncClient =
        S3AsyncClient.builder()
            .credentialsProvider(credentialsProvider)
            .region(Region.of(region))
            .build();
    this.bucketName = bucketName;
  }

  @Override
  public void close() {
    if (textractClient != null && s3AsyncClient != null) {
      try {
        textractClient.close();
        s3AsyncClient.close();
        LOGGER.debug("TextractClient and S3AsyncClient closed successfully");
      } catch (Exception e) {
        LOGGER.warn("Error while closing TextractClient/S3AsyncClient", e);
      }
    }
  }

  @Override
  public String extract(Document document) {
    try {
      S3Object s3Object = AwsUtil.buildS3ObjectFromDocument(document, bucketName, s3AsyncClient);
      LOGGER.debug("Starting polling task for text detection with document: {}", s3Object.name());
      List<Block> allBlocks = processTextDetection(s3Object);
      AwsUtil.deleteS3ObjectFromBucketAsync(s3Object.name(), bucketName, s3AsyncClient);
      return allBlocks.stream()
          .filter(block -> block.blockType().equals(BlockType.LINE))
          .map(Block::text)
          .collect(Collectors.joining("\n"));
    } catch (Exception e) {
      LOGGER.error("Error while processing text detection", e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public StructuredExtractionResponse runDocumentAnalysis(Document document) {
    try {
      S3Object s3Object = AwsUtil.buildS3ObjectFromDocument(document, bucketName, s3AsyncClient);
      List<Block> allBlocks = processDocumentAnalysis(s3Object);
      AwsUtil.deleteS3ObjectFromBucketAsync(s3Object.name(), bucketName, s3AsyncClient);
      return extractDataFromDocument(allBlocks);
    } catch (Exception e) {
      LOGGER.error("Error while processing text detection", e);
      throw new RuntimeException(e);
    }
  }

  private List<Block> processTextDetection(S3Object s3Object) throws Exception {
    final StartDocumentTextDetectionRequest startDocumentTextDetectionRequest =
        StartDocumentTextDetectionRequest.builder()
            .documentLocation(AwsUtil.buildDocumentLocation(s3Object))
            .build();

    String jobId =
        textractClient.startDocumentTextDetection(startDocumentTextDetectionRequest).jobId();

    List<Block> allBlocks;
    try (ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor()) {
      TextractTextDetectionTask firstTask = prepareTextractTextDetectionTask(jobId, textractClient);
      GetDocumentTextDetectionResponse firstDocumentResult =
          executeTextDetectionTask(firstTask, 0, executorService);

      List<Block> blocks = firstDocumentResult.blocks();
      JobStatus jobStatus = firstDocumentResult.jobStatus();
      String nextToken = firstDocumentResult.nextToken();

      allBlocks = new ArrayList<>(blocks);
      boolean isAnalysisFinished = jobStatus.equals(JobStatus.SUCCEEDED) && nextToken == null;
      String currentNextToken = nextToken;

      LOGGER.debug(
          "Text Detection - Initial status: document={}, jobStatus={}, nextToken present ={}, isAnalysisFinished={}, blocks count={}",
          s3Object.name(),
          jobStatus,
          nextToken != null,
          isAnalysisFinished,
          blocks.size());

      while (!isAnalysisFinished) {
        TextractTextDetectionTask nextTask =
            prepareTextractTextDetectionTask(jobId, textractClient, currentNextToken);
        GetDocumentTextDetectionResponse nextDocumentResult =
            executeTextDetectionTask(nextTask, DELAY_BETWEEN_POLLING, executorService);

        JobStatus newJobStatus = nextDocumentResult.jobStatus();
        List<Block> nextBlocks = nextDocumentResult.blocks();
        String statusMessage = nextDocumentResult.statusMessage();
        String newNextToken = nextDocumentResult.nextToken();

        switch (newJobStatus) {
          case SUCCEEDED -> {
            allBlocks.addAll(nextBlocks);
            isAnalysisFinished = newNextToken == null;
            currentNextToken = newNextToken;
            LOGGER.debug(
                "Text Detection - Status SUCCEEDED: document={}, nextToken present ={}, isAnalysisFinished={}, new blocks count={}, total blocks count={}",
                s3Object.name(),
                newNextToken != null,
                isAnalysisFinished,
                nextBlocks.size(),
                allBlocks.size());
          }
          case FAILED -> throw new ConnectorException(statusMessage);
          default -> {
            allBlocks.addAll(nextBlocks);
            currentNextToken = newNextToken;
            LOGGER.debug(
                "Text Detection - Status {}: document={}, nextToken present ={}, new blocks count={}, total blocks count={}",
                newJobStatus,
                s3Object.name(),
                newNextToken != null,
                nextBlocks.size(),
                allBlocks.size());
          }
        }
      }
    }

    return allBlocks;
  }

  private List<Block> processDocumentAnalysis(S3Object s3Object) throws Exception {
    List<FeatureType> featureTypes = new ArrayList<>();
    featureTypes.add(FeatureType.FORMS);
    featureTypes.add(FeatureType.TABLES);

    final StartDocumentAnalysisRequest startDocumentAnalysisRequest =
        StartDocumentAnalysisRequest.builder()
            .featureTypes(featureTypes)
            .documentLocation(AwsUtil.buildDocumentLocation(s3Object))
            .build();

    String jobId = textractClient.startDocumentAnalysis(startDocumentAnalysisRequest).jobId();

    List<Block> allBlocks;
    try (ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor()) {
      TextractAnalysisTask firstTask = prepareTextractAnalysisTask(jobId, textractClient);
      GetDocumentAnalysisResponse firstDocumentResult =
          executeAnalysisTask(firstTask, 0, executorService);

      List<Block> blocks = firstDocumentResult.blocks();
      JobStatus jobStatus = firstDocumentResult.jobStatus();
      String nextToken = firstDocumentResult.nextToken();

      allBlocks = new ArrayList<>(blocks);
      boolean isAnalysisFinished = jobStatus.equals(JobStatus.SUCCEEDED) && nextToken == null;
      String currentNextToken = nextToken;

      LOGGER.debug(
          "Document Analysis - Initial status: document={}, jobStatus={}, nextToken present ={}, isAnalysisFinished={}, blocks count={}",
          s3Object.name(),
          jobStatus,
          nextToken != null,
          isAnalysisFinished,
          blocks.size());

      while (!isAnalysisFinished) {
        TextractAnalysisTask nextTask =
            prepareTextractAnalysisTask(jobId, textractClient, currentNextToken);
        GetDocumentAnalysisResponse nextDocumentResult =
            executeAnalysisTask(nextTask, DELAY_BETWEEN_POLLING, executorService);

        JobStatus newJobStatus = nextDocumentResult.jobStatus();
        List<Block> nextBlocks = nextDocumentResult.blocks();
        String statusMessage = nextDocumentResult.statusMessage();
        String newNextToken = nextDocumentResult.nextToken();

        switch (newJobStatus) {
          case SUCCEEDED -> {
            allBlocks.addAll(nextBlocks);
            isAnalysisFinished = newNextToken == null;
            currentNextToken = newNextToken;
            LOGGER.debug(
                "Document Analysis - Status SUCCEEDED: document={}, nextToken present ={}, isAnalysisFinished={}, new blocks count={}, total blocks count={}",
                s3Object.name(),
                newNextToken != null,
                isAnalysisFinished,
                nextBlocks.size(),
                allBlocks.size());
          }
          case FAILED -> throw new ConnectorException(statusMessage);
          default -> {
            allBlocks.addAll(nextBlocks);
            currentNextToken = newNextToken;
            LOGGER.debug(
                "Document Analysis - Status {}: document={}, nextToken present ={}, new blocks count={}, total blocks count={}",
                newJobStatus,
                s3Object.name(),
                newNextToken != null,
                nextBlocks.size(),
                allBlocks.size());
          }
        }
      }
    }

    return allBlocks;
  }

  private TextractAnalysisTask prepareTextractAnalysisTask(
      String jobId, TextractClient textractClient) {
    return prepareTextractAnalysisTask(jobId, textractClient, null);
  }

  private TextractAnalysisTask prepareTextractAnalysisTask(
      String jobId, TextractClient textractClient, String nextToken) {
    GetDocumentAnalysisRequest.Builder requestBuilder =
        GetDocumentAnalysisRequest.builder().jobId(jobId).maxResults(MAX_RESULT);

    if (nextToken != null) {
      requestBuilder.nextToken(nextToken);
    }

    return new TextractAnalysisTask(requestBuilder.build(), textractClient);
  }

  private GetDocumentAnalysisResponse executeAnalysisTask(
      TextractAnalysisTask task, long delay, ScheduledExecutorService executorService)
      throws Exception {
    ScheduledFuture<GetDocumentAnalysisResponse> nextDocumentResultFuture =
        executorService.schedule(task, delay, SECONDS);
    return nextDocumentResultFuture.get();
  }

  private TextractTextDetectionTask prepareTextractTextDetectionTask(
      String jobId, TextractClient textractClient) {
    return prepareTextractTextDetectionTask(jobId, textractClient, null);
  }

  private TextractTextDetectionTask prepareTextractTextDetectionTask(
      String jobId, TextractClient textractClient, String nextToken) {
    GetDocumentTextDetectionRequest.Builder requestBuilder =
        GetDocumentTextDetectionRequest.builder().jobId(jobId).maxResults(MAX_RESULT);

    if (nextToken != null) {
      requestBuilder.nextToken(nextToken);
    }

    return new TextractTextDetectionTask(requestBuilder.build(), textractClient);
  }

  private GetDocumentTextDetectionResponse executeTextDetectionTask(
      TextractTextDetectionTask task, long delay, ScheduledExecutorService executorService)
      throws Exception {
    ScheduledFuture<GetDocumentTextDetectionResponse> nextDocumentResultFuture =
        executorService.schedule(task, delay, SECONDS);
    return nextDocumentResultFuture.get();
  }

  private StructuredExtractionResponse extractDataFromDocument(List<Block> blocks) {
    Map<String, Object> keyValuePairs = new HashMap<>();
    Map<String, Object> confidenceScores = new HashMap<>();
    Map<String, Polygon> geometry = new HashMap<>();
    Map<String, Block> blockMap =
        blocks.stream().collect(Collectors.toMap(Block::id, block -> block));
    Map<String, Integer> keyOccurrences = new HashMap<>();

    // extract key-value pairs
    blocks.stream()
        .filter(
            block ->
                block.blockType().equals(BlockType.KEY_VALUE_SET)
                    && block.entityTypes().contains(EntityType.KEY))
        .forEach(
            keyBlock -> {
              String originalKey = getTextFromRelationships(keyBlock, blockMap);
              String key = originalKey;

              // Handle duplicate keys by adding a suffix
              if (keyValuePairs.containsKey(key)) {
                int count = keyOccurrences.getOrDefault(originalKey, 1) + 1;
                keyOccurrences.put(originalKey, count);
                key = originalKey + " " + count;
              } else {
                keyOccurrences.put(originalKey, 1);
              }

              Block valueBlock =
                  blockMap.get(
                      keyBlock.relationships().stream()
                          .filter(relation -> relation.type().equals(RelationshipType.VALUE))
                          .flatMap(relation -> relation.ids().stream())
                          .findFirst()
                          .orElseThrow(() -> new ConnectorException("Value block not found")));

              String value = getTextFromRelationships(valueBlock, blockMap);

              Float keyConfidence = keyBlock.confidence();
              Float valueConfidence = valueBlock.confidence();

              // Use the lower of the two confidence scores (conservative approach)
              float combinedConfidence = Math.min(keyConfidence, valueConfidence);

              keyValuePairs.put(key, value);
              confidenceScores.put(key, combinedConfidence / 100); // Convert to percentage

              var keyPolygonMap = keyBlock.geometry().polygon();
              var valuePolygonMap = valueBlock.geometry().polygon();
              geometry.put(
                  key,
                  new Polygon(keyBlock.page(), getBoundingPolygon(keyPolygonMap, valuePolygonMap)));
            });

    // extract table data
    List<Block> tables =
        blocks.stream().filter(block -> block.blockType().equals(BlockType.TABLE)).toList();

    for (Block table : tables) {
      List<List<String>> tableData = new ArrayList<>();
      List<List<Float>> tableConfidence = new ArrayList<>();
      table.relationships().stream()
          .filter(relation -> relation.type().equals(RelationshipType.CHILD))
          .flatMap(relation -> relation.ids().stream())
          .map(blockMap::get)
          .filter(block -> block.blockType().equals(BlockType.CELL))
          .forEach(
              block -> {
                String cellText = getTextFromRelationships(block, blockMap);
                int colIndex = block.columnIndex() - 1;
                int rowIndex = block.rowIndex() - 1;

                // ensure the outer list has enough rows
                while (tableData.size() <= rowIndex) {
                  tableData.add(new ArrayList<>());
                  tableConfidence.add(new ArrayList<>());
                }

                List<String> row = tableData.get(rowIndex);
                List<Float> confidenceRow = tableConfidence.get(rowIndex);

                // ensure the row list has enough columns
                while (row.size() <= colIndex) {
                  row.add("");
                }
                while (confidenceRow.size() <= colIndex) {
                  confidenceRow.add(0.0f);
                }

                row.set(colIndex, cellText);
                confidenceRow.set(
                    colIndex, block.confidence() / 100); // Convert to percentage as Float
              });
      String tableKey = "table " + (tables.indexOf(table) + 1);
      keyValuePairs.put(tableKey, tableData);
      confidenceScores.put(tableKey, tableConfidence);
      List<PolygonPoint> tablePolygons =
          table.geometry().polygon().stream()
              .map(point -> new PolygonPoint(point.x(), point.y()))
              .toList();
      geometry.put(tableKey, new Polygon(table.page(), tablePolygons));
    }

    return new StructuredExtractionResponse(keyValuePairs, confidenceScores, geometry);
  }

  private String getTextFromRelationships(Block block, Map<String, Block> blockMap) {
    if (block.relationships() == null) {
      return "";
    }

    return block.relationships().stream()
        .filter(relation -> relation.type().equals(RelationshipType.CHILD))
        .flatMap(relation -> relation.ids().stream())
        .map(blockMap::get)
        .map(
            valueBlock -> {
              if (valueBlock.blockType().equals(BlockType.SELECTION_ELEMENT)) {
                return valueBlock.selectionStatus().equals(SelectionStatus.SELECTED)
                    ? "true"
                    : "false";
              } else {
                return valueBlock.text();
              }
            })
        .collect(Collectors.joining(" "));
  }

  private List<PolygonPoint> getBoundingPolygon(List<Point> polygon1, List<Point> polygon2) {
    float minX = Float.MAX_VALUE;
    float minY = Float.MAX_VALUE;
    float maxX = Float.MIN_VALUE;
    float maxY = Float.MIN_VALUE;

    // Process all points from first polygon
    for (Point point : polygon1) {
      minX = Math.min(minX, point.x());
      minY = Math.min(minY, point.y());
      maxX = Math.max(maxX, point.x());
      maxY = Math.max(maxY, point.y());
    }

    // Process all points from second polygon
    for (Point point : polygon2) {
      minX = Math.min(minX, point.x());
      minY = Math.min(minY, point.y());
      maxX = Math.max(maxX, point.x());
      maxY = Math.max(maxY, point.y());
    }

    // Create the 4 corners of the bounding rectangle (top-left, top-right, bottom-right,
    // bottom-left)
    return List.of(
        new PolygonPoint(minX, minY),
        new PolygonPoint(maxX, minY),
        new PolygonPoint(maxX, maxY),
        new PolygonPoint(minX, maxY));
  }
}
