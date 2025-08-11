/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.idp.extraction.model.TextractTextDetectionTask;
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

  private List<Block> processDocument(
      Document document,
      String bucketName,
      TextractClient textractClient,
      S3AsyncClient s3AsyncClient,
      TextractProcessType processType)
      throws Exception {
    S3Object s3Object = AwsS3Util.buildS3ObjectFromDocument(document, bucketName, s3AsyncClient);
    LOGGER.debug("Starting polling task for {} with document: {}", processType, s3Object.name());
    List<Block> allBlocks;
    if (processType == TextractProcessType.DOCUMENT_ANALYSIS) {
      throw new ConnectorException(
          "Document Analysis is not supported in this version. Please use Text Detection instead.");
    } else {
      allBlocks = processTextDetection(s3Object, textractClient);
    }
    AwsS3Util.deleteS3ObjectFromBucketAsync(s3Object.name(), bucketName, s3AsyncClient);
    return allBlocks;
  }

  private List<Block> processTextDetection(S3Object s3Object, TextractClient textractClient)
      throws Exception {
    final StartDocumentTextDetectionRequest startDocumentTextDetectionRequest =
        StartDocumentTextDetectionRequest.builder()
            .documentLocation(AwsS3Util.buildDocumentLocation(s3Object))
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
}
