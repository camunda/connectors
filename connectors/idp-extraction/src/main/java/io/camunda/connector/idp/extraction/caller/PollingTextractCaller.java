/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.model.TextractTask;
import io.camunda.connector.idp.extraction.utils.AwsS3Util;
import io.camunda.document.Document;
import java.util.ArrayList;
import java.util.List;
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

    S3Object s3Object = AwsS3Util.buildS3ObjectFromDocument(document, bucketName, s3AsyncClient);

    LOGGER.debug("Starting polling task for document analysis with document: {}", s3Object.name());

    List<FeatureType> featureTypes = new ArrayList<>();
    featureTypes.add(FeatureType.FORMS);
    featureTypes.add(FeatureType.TABLES);

    final StartDocumentAnalysisRequest startDocumentAnalysisRequest =
        StartDocumentAnalysisRequest.builder()
            .featureTypes(featureTypes)
            .documentLocation(AwsS3Util.buildDocumentLocation(s3Object))
            .build();

    final StartDocumentAnalysisResponse response =
        textractClient.startDocumentAnalysis(startDocumentAnalysisRequest);

    List<Block> allBlocks;
    try (ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor()) {
      final String jobId = response.jobId();
      final TextractTask firstTextractTask = prepareTextractTask(jobId, textractClient);
      final GetDocumentAnalysisResponse firstDocumentResult =
          executeTask(firstTextractTask, 0, executorService);

      allBlocks = new ArrayList<>(firstDocumentResult.blocks());
      boolean isAnalysisFinished = firstDocumentResult.jobStatus().equals(JobStatus.SUCCEEDED);

      while (!isAnalysisFinished) {
        final TextractTask nextTextractTask = prepareTextractTask(jobId, textractClient);
        GetDocumentAnalysisResponse nextDocumentResult =
            executeTask(nextTextractTask, DELAY_BETWEEN_POLLING, executorService);
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

    return allBlocks.stream()
        .filter(block -> block.blockType().equals(BlockType.LINE))
        .map(Block::text)
        .collect(Collectors.joining("\n"));
  }

  private TextractTask prepareTextractTask(String jobId, TextractClient textractClient) {
    GetDocumentAnalysisRequest documentAnalysisRequest =
        GetDocumentAnalysisRequest.builder().jobId(jobId).maxResults(MAX_RESULT).build();

    return new TextractTask(documentAnalysisRequest, textractClient);
  }

  private GetDocumentAnalysisResponse executeTask(
      TextractTask task, long delay, ScheduledExecutorService executorService) throws Exception {
    ScheduledFuture<GetDocumentAnalysisResponse> nextDocumentResultFuture =
        executorService.schedule(task, delay, SECONDS);
    return nextDocumentResultFuture.get();
  }
}
