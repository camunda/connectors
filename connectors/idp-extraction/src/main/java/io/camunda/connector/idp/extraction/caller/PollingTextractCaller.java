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
import io.camunda.connector.idp.extraction.model.TextractTask;
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

  public String call(
      Document document,
      String bucketName,
      TextractClient textractClient,
      S3AsyncClient s3AsyncClient)
      throws Exception {

    List<Block> allBlocks = processDocument(document, bucketName, textractClient, s3AsyncClient);

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

    List<Block> allBlocks = processDocument(document, bucketName, textractClient, s3AsyncClient);

    return extractKeyValuePairsWithConfidence(allBlocks);
  }

  private List<Block> processDocument(
      Document document,
      String bucketName,
      TextractClient textractClient,
      S3AsyncClient s3AsyncClient)
      throws Exception {

    S3Object s3Object = AwsS3Util.buildS3ObjectFromDocument(document, bucketName, s3AsyncClient);

    LOGGER.debug(
        "Starting polling task for document text detection with document: {}", s3Object.name());

    final StartDocumentTextDetectionRequest startDocumentTextDetectionRequest =
        StartDocumentTextDetectionRequest.builder()
            .documentLocation(AwsS3Util.buildDocumentLocation(s3Object))
            .build();

    final StartDocumentTextDetectionResponse response =
        textractClient.startDocumentTextDetection(startDocumentTextDetectionRequest);

    List<Block> allBlocks;
    try (ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor()) {
      final String jobId = response.jobId();
      final TextractTask firstTextractTask =
          prepareTextractTextDetectionTask(jobId, textractClient);
      final GetDocumentTextDetectionResponse firstDocumentResult =
          executeTextDetectionTask(firstTextractTask, 0, executorService);

      allBlocks = new ArrayList<>(firstDocumentResult.blocks());
      boolean isAnalysisFinished = firstDocumentResult.jobStatus().equals(JobStatus.SUCCEEDED);

      while (!isAnalysisFinished) {
        final TextractTask nextTextractTask =
            prepareTextractTextDetectionTask(jobId, textractClient);
        GetDocumentTextDetectionResponse nextDocumentResult =
            executeTextDetectionTask(nextTextractTask, DELAY_BETWEEN_POLLING, executorService);
        JobStatus newJobStatus = nextDocumentResult.jobStatus();

        switch (newJobStatus) {
          case SUCCEEDED -> {
            isAnalysisFinished = true;
            allBlocks.addAll(nextDocumentResult.blocks());
          }
          case FAILED -> throw new ConnectorException(nextDocumentResult.statusMessage());
          default -> allBlocks.addAll(nextDocumentResult.blocks());
        }
      }
    }

    AwsS3Util.deleteS3ObjectFromBucketAsync(s3Object.name(), bucketName, s3AsyncClient);

    return allBlocks;
  }

  private TextractTask prepareTextractTextDetectionTask(
      String jobId, TextractClient textractClient) {
    GetDocumentTextDetectionRequest documentTextDetectionRequest =
        GetDocumentTextDetectionRequest.builder().jobId(jobId).maxResults(MAX_RESULT).build();

    return new TextractTask(documentTextDetectionRequest, textractClient);
  }

  private GetDocumentTextDetectionResponse executeTextDetectionTask(
      TextractTask task, long delay, ScheduledExecutorService executorService) throws Exception {
    ScheduledFuture<GetDocumentTextDetectionResponse> nextDocumentResultFuture =
        executorService.schedule(task, delay, SECONDS);
    return nextDocumentResultFuture.get();
  }

  private StructuredExtractionResponse extractKeyValuePairsWithConfidence(List<Block> blocks) {
    Map<String, String> keyValuePairs = new HashMap<>();
    Map<String, Float> confidenceScores = new HashMap<>();
    Map<String, Block> blockMap =
        blocks.stream().collect(Collectors.toMap(Block::id, block -> block));

    blocks.stream()
        .filter(
            block ->
                block.blockType().equals(BlockType.KEY_VALUE_SET)
                    && block.entityTypes().contains(EntityType.KEY))
        .forEach(
            keyBlock -> {
              String key = getTextFromRelationships(keyBlock, blockMap);
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
        .map(Block::text)
        .collect(Collectors.joining(" "));
  }
}
