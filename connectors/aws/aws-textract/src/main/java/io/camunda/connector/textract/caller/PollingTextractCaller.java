/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.caller;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.AmazonTextractAsync;
import com.amazonaws.services.textract.model.*;
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

public class PollingTextractCalller implements TextractCaller<GetDocumentAnalysisResult> {
  public static final long DELAY_BETWEEN_POLLING = 5;

  public static final int MAX_RESULT = 1000;

  private static final Logger LOGGER = LoggerFactory.getLogger(PollingTextractCalller.class);

  @Override
  public GetDocumentAnalysisResult call(
      TextractRequestData requestData, AmazonTextract textractClient) throws Exception {
    LOGGER.debug("Starting polling task for document analysis with request data: {}", requestData);
    final StartDocumentAnalysisRequest startDocReq =
        new StartDocumentAnalysisRequest()
            .withFeatureTypes(this.prepareFeatureTypes(requestData))
            .withDocumentLocation(this.prepareDocumentLocation(requestData));

    final StartDocumentAnalysisResult result = textractClient.startDocumentAnalysis(startDocReq);

    GetDocumentAnalysisResult lastDocumentResult;
    List<Block> allBlocks;
    try (ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor()) {
      final String jobId = result.getJobId();
      final TextractTask firstTextractTask = prepareTextractTask(jobId, null, textractClient);
      final GetDocumentAnalysisResult firstDocumentResult =
          executeTask(firstTextractTask, 0, executorService);

      allBlocks = new ArrayList<>(firstDocumentResult.getBlocks());
      lastDocumentResult = firstDocumentResult;
      String nextToken = firstDocumentResult.getNextToken();

      while (StringUtils.isNoneEmpty(nextToken)) {
        final TextractTask nextTextractTask = prepareTextractTask(jobId, nextToken, textractClient);
        GetDocumentAnalysisResult nextDocumentResult =
            executeTask(nextTextractTask, DELAY_BETWEEN_POLLING, executorService);
        nextToken = nextDocumentResult.getNextToken();
        allBlocks.addAll(nextDocumentResult.getBlocks());
        lastDocumentResult = nextDocumentResult;
      }
    }

    lastDocumentResult.setBlocks(allBlocks);
    return lastDocumentResult;
  }

  private TextractTask prepareTextractTask(
      String jobId, String nextToken, AmazonTextract textractClient) {
    GetDocumentAnalysisRequest documentAnalysisReq =
        new GetDocumentAnalysisRequest().withJobId(jobId).withMaxResults(MAX_RESULT);

    if (StringUtils.isNoneEmpty(nextToken)) {
      documentAnalysisReq.withNextToken(nextToken);
    }

    return new TextractTask(documentAnalysisReq, (AmazonTextractAsync) textractClient);
  }

  private GetDocumentAnalysisResult executeTask(
      TextractTask task, long delay, ScheduledExecutorService executorService) throws Exception {
    ScheduledFuture<GetDocumentAnalysisResult> nextDocumentResultFuture =
        executorService.schedule(task, delay, SECONDS);
    return nextDocumentResultFuture.get();
  }
}
