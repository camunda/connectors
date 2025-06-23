/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.model.Polygon;
import io.camunda.connector.idp.extraction.model.PolygonPoint;
import io.camunda.connector.idp.extraction.model.StructuredExtractionResponse;
import io.camunda.connector.idp.extraction.model.TextractAnalysisTask;
import io.camunda.connector.idp.extraction.model.TextractTextDetectionTask;
import io.camunda.connector.idp.extraction.utils.AwsS3Util;
import io.camunda.document.Document;
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
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

public class PollingTextractCaller {
  public static final long DELAY_BETWEEN_POLLING = 5;

  public static final int MAX_RESULT = 1000;

  private static final Logger LOGGER = LoggerFactory.getLogger(PollingTextractCaller.class);

  private enum TextractProcessType {
    TEXT_DETECTION,
    DOCUMENT_ANALYSIS
  }

  public String call(
      Document document,
      String bucketName,
      TextractClient textractClient,
      S3AsyncClient s3AsyncClient)
      throws Exception {

    List<Block> allBlocks =
        processDocument(
            document,
            bucketName,
            textractClient,
            s3AsyncClient,
            TextractProcessType.TEXT_DETECTION);

    return allBlocks.stream()
        .filter(block -> block.blockType().equals(BlockType.LINE))
        .map(Block::text)
        .collect(Collectors.joining("\n"));
  }

  public StructuredExtractionResponse extractKeyValuePairsWithConfidence(
      Document document,
      String bucketName,
      TextractClient textractClient,
      S3AsyncClient s3AsyncClient)
      throws Exception {

    List<Block> allBlocks =
        processDocument(
            document,
            bucketName,
            textractClient,
            s3AsyncClient,
            TextractProcessType.DOCUMENT_ANALYSIS);
    return extractDataFromDocument(allBlocks);
  }

  private List<Block> processDocument(
      Document document,
      String bucketName,
      TextractClient textractClient,
      S3AsyncClient s3AsyncClient,
      TextractProcessType processType)
      throws Exception {

    S3Object s3Object = AwsS3Util.buildS3ObjectFromDocument(document, bucketName, s3AsyncClient);

    LOGGER.debug("Starting polling task for {} with document: {}", processType, s3Object.name());

    String jobId;
    if (processType == TextractProcessType.DOCUMENT_ANALYSIS) {
      List<FeatureType> featureTypes = new ArrayList<>();
      featureTypes.add(FeatureType.FORMS);
      featureTypes.add(FeatureType.TABLES);

      final StartDocumentAnalysisRequest startDocumentAnalysisRequest =
          StartDocumentAnalysisRequest.builder()
              .featureTypes(featureTypes)
              .documentLocation(AwsS3Util.buildDocumentLocation(s3Object))
              .build();

      jobId = textractClient.startDocumentAnalysis(startDocumentAnalysisRequest).jobId();
    } else {
      final StartDocumentTextDetectionRequest startDocumentTextDetectionRequest =
          StartDocumentTextDetectionRequest.builder()
              .documentLocation(AwsS3Util.buildDocumentLocation(s3Object))
              .build();

      jobId = textractClient.startDocumentTextDetection(startDocumentTextDetectionRequest).jobId();
    }

    List<Block> allBlocks;
    try (ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor()) {
      Object firstTask =
          processType == TextractProcessType.DOCUMENT_ANALYSIS
              ? prepareTextractAnalysisTask(jobId, textractClient)
              : prepareTextractTextDetectionTask(jobId, textractClient);

      Object firstDocumentResult =
          processType == TextractProcessType.DOCUMENT_ANALYSIS
              ? executeAnalysisTask((TextractAnalysisTask) firstTask, 0, executorService)
              : executeTextDetectionTask((TextractTextDetectionTask) firstTask, 0, executorService);

      List<Block> blocks =
          processType == TextractProcessType.DOCUMENT_ANALYSIS
              ? ((GetDocumentAnalysisResponse) firstDocumentResult).blocks()
              : ((GetDocumentTextDetectionResponse) firstDocumentResult).blocks();

      JobStatus jobStatus =
          processType == TextractProcessType.DOCUMENT_ANALYSIS
              ? ((GetDocumentAnalysisResponse) firstDocumentResult).jobStatus()
              : ((GetDocumentTextDetectionResponse) firstDocumentResult).jobStatus();

      allBlocks = new ArrayList<>(blocks);
      boolean isAnalysisFinished = jobStatus.equals(JobStatus.SUCCEEDED);

      while (!isAnalysisFinished) {
        Object nextTask =
            processType == TextractProcessType.DOCUMENT_ANALYSIS
                ? prepareTextractAnalysisTask(jobId, textractClient)
                : prepareTextractTextDetectionTask(jobId, textractClient);

        Object nextDocumentResult =
            processType == TextractProcessType.DOCUMENT_ANALYSIS
                ? executeAnalysisTask(
                    (TextractAnalysisTask) nextTask, DELAY_BETWEEN_POLLING, executorService)
                : executeTextDetectionTask(
                    (TextractTextDetectionTask) nextTask, DELAY_BETWEEN_POLLING, executorService);

        JobStatus newJobStatus =
            processType == TextractProcessType.DOCUMENT_ANALYSIS
                ? ((GetDocumentAnalysisResponse) nextDocumentResult).jobStatus()
                : ((GetDocumentTextDetectionResponse) nextDocumentResult).jobStatus();

        List<Block> nextBlocks =
            processType == TextractProcessType.DOCUMENT_ANALYSIS
                ? ((GetDocumentAnalysisResponse) nextDocumentResult).blocks()
                : ((GetDocumentTextDetectionResponse) nextDocumentResult).blocks();

        String statusMessage =
            processType == TextractProcessType.DOCUMENT_ANALYSIS
                ? ((GetDocumentAnalysisResponse) nextDocumentResult).statusMessage()
                : ((GetDocumentTextDetectionResponse) nextDocumentResult).statusMessage();

        switch (newJobStatus) {
          case SUCCEEDED -> {
            isAnalysisFinished = true;
            allBlocks.addAll(nextBlocks);
          }
          case FAILED -> throw new ConnectorException(statusMessage);
          default -> allBlocks.addAll(nextBlocks);
        }
      }
    }

    AwsS3Util.deleteS3ObjectFromBucketAsync(s3Object.name(), bucketName, s3AsyncClient);

    return allBlocks;
  }

  private TextractAnalysisTask prepareTextractAnalysisTask(
      String jobId, TextractClient textractClient) {
    GetDocumentAnalysisRequest documentAnalysisRequest =
        GetDocumentAnalysisRequest.builder().jobId(jobId).maxResults(MAX_RESULT).build();

    return new TextractAnalysisTask(documentAnalysisRequest, textractClient);
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
    GetDocumentTextDetectionRequest documentTextDetectionRequest =
        GetDocumentTextDetectionRequest.builder().jobId(jobId).maxResults(MAX_RESULT).build();

    return new TextractTextDetectionTask(documentTextDetectionRequest, textractClient);
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
