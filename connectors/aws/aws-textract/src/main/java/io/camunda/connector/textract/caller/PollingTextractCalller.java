/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.caller;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.camunda.connector.textract.model.TextractRequestData;
import io.camunda.connector.textract.model.TextractTask;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.GetDocumentAnalysisRequest;
import software.amazon.awssdk.services.textract.model.GetDocumentAnalysisResponse;
import software.amazon.awssdk.services.textract.model.StartDocumentAnalysisRequest;
import software.amazon.awssdk.services.textract.model.StartDocumentAnalysisResponse;

public class PollingTextractCalller implements TextractCaller<GetDocumentAnalysisResponse> {
  public static final long DELAY_BETWEEN_POLLING = 5;

  public static final int MAX_RESULT = 1000;

  private static final Logger LOGGER = LoggerFactory.getLogger(PollingTextractCalller.class);

  @Override
  public GetDocumentAnalysisResponse call(
      TextractRequestData requestData, TextractClient textractClient) throws Exception {
    LOGGER.debug("Starting polling task for document analysis with request data: {}", requestData);
    final StartDocumentAnalysisRequest startDocReq =
        StartDocumentAnalysisRequest.builder()
            .featureTypes(this.prepareFeatureTypes(requestData))
            .documentLocation(this.prepareDocumentLocation(requestData))
            .clientRequestToken(requestData.clientRequestToken())
            .jobTag(requestData.jobTag())
            .kmsKeyId(requestData.kmsKeyId())
            .build();

    final StartDocumentAnalysisResponse result = textractClient.startDocumentAnalysis(startDocReq);

    GetDocumentAnalysisResponse lastDocumentResult;
    List<software.amazon.awssdk.services.textract.model.Block> allBlocks;
    try (ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor()) {
      final String jobId = result.jobId();
      final TextractTask firstTextractTask = prepareTextractTask(jobId, null, textractClient);
      final GetDocumentAnalysisResponse firstDocumentResult =
          executeTask(firstTextractTask, 0, executorService);

      allBlocks = new ArrayList<>(firstDocumentResult.blocks());
      lastDocumentResult = firstDocumentResult;
      String nextToken = firstDocumentResult.nextToken();

      while (StringUtils.isNoneEmpty(nextToken)) {
        final TextractTask nextTextractTask = prepareTextractTask(jobId, nextToken, textractClient);
        GetDocumentAnalysisResponse nextDocumentResult =
            executeTask(nextTextractTask, DELAY_BETWEEN_POLLING, executorService);
        nextToken = nextDocumentResult.nextToken();
        allBlocks.addAll(nextDocumentResult.blocks());
        lastDocumentResult = nextDocumentResult;
      }
    }

    return lastDocumentResult.toBuilder().blocks(allBlocks).build();
  }

  private TextractTask prepareTextractTask(
      String jobId, String nextToken, TextractClient textractClient) {
    GetDocumentAnalysisRequest documentAnalysisReq =
        GetDocumentAnalysisRequest.builder().jobId(jobId).maxResults(MAX_RESULT).build();

    if (StringUtils.isNoneEmpty(nextToken)) {
      documentAnalysisReq = documentAnalysisReq.toBuilder().nextToken(nextToken).build();
    }

    return new TextractTask(documentAnalysisReq, textractClient);
  }

  private GetDocumentAnalysisResponse executeTask(
      TextractTask task, long delay, ScheduledExecutorService executorService) throws Exception {
    ScheduledFuture<GetDocumentAnalysisResponse> nextDocumentResultFuture =
        executorService.schedule(task, delay, SECONDS);
    return nextDocumentResultFuture.get();
  }
}
