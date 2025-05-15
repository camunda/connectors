/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.camunda.connector.api.error.ConnectorException;
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
    Map<String, String> keyValuePairs = new HashMap<>();
    Map<String, Float> confidenceScores = new HashMap<>();
    Map<String, Block> blockMap =
        blocks.stream().collect(Collectors.toMap(Block::id, block -> block));
    Map<String, Integer> keyOccurrences = new HashMap<>();

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
            });

    return new StructuredExtractionResponse(keyValuePairs, confidenceScores);
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
}
