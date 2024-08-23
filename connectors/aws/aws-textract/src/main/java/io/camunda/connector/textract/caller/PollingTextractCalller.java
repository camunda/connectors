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
import com.amazonaws.services.textract.model.GetDocumentAnalysisRequest;
import com.amazonaws.services.textract.model.GetDocumentAnalysisResult;
import com.amazonaws.services.textract.model.StartDocumentAnalysisRequest;
import com.amazonaws.services.textract.model.StartDocumentAnalysisResult;
import io.camunda.connector.textract.model.TextractRequestData;
import io.camunda.connector.textract.model.TextractTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PollingTextractCalller implements TextractCaller<GetDocumentAnalysisResult> {
  public static final long DELAY_BETWEEN_POLLING = 5;

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
    final var documentAnalysisReq = new GetDocumentAnalysisRequest().withJobId(result.getJobId());
    final var textractTask =
        new TextractTask(documentAnalysisReq, (AmazonTextractAsync) textractClient);

    ScheduledFuture<GetDocumentAnalysisResult> future;
    try (ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor()) {
      future = executorService.schedule(textractTask, 0, SECONDS);

      while (continuePolling(future.get().getJobStatus())) {
        future = executorService.schedule(textractTask, DELAY_BETWEEN_POLLING, SECONDS);
      }
    }

    return future.get();
  }

  private boolean continuePolling(String status) {
    return "IN_PROGRESS".equals(status);
  }
}
