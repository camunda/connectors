/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.caller;

import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.model.*;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.textract.model.TextractRequestData;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PollingTextractCaller implements TextractCaller<GetDocumentAnalysisResult> {
  public static final int MAX_RESULT = 1000;

  private static final Logger LOGGER = LoggerFactory.getLogger(PollingTextractCaller.class);

  @Override
  public GetDocumentAnalysisResult call(
      TextractRequestData requestData, AmazonTextract textractClient) throws Exception {

    final StartDocumentAnalysisRequest startDocReq =
        new StartDocumentAnalysisRequest()
            .withFeatureTypes(this.prepareFeatureTypes(requestData))
            .withQueriesConfig(prepareQueryConfig(requestData))
            .withDocumentLocation(this.prepareDocumentLocation(requestData));

    final StartDocumentAnalysisResult result = textractClient.startDocumentAnalysis(startDocReq);
    final String jobId = result.getJobId();

    LOGGER.debug("Started document analysis with jobId: {}", jobId);

    GetDocumentAnalysisResult firstResult = pollUntilComplete(jobId, textractClient);

    List<Block> allBlocks = new ArrayList<>(firstResult.getBlocks());
    GetDocumentAnalysisResult lastResult = firstResult;
    String nextToken = firstResult.getNextToken();

    while (StringUtils.isNotEmpty(nextToken)) {
      GetDocumentAnalysisRequest nextRequest =
          new GetDocumentAnalysisRequest()
              .withJobId(jobId)
              .withMaxResults(MAX_RESULT)
              .withNextToken(nextToken);

      GetDocumentAnalysisResult nextResult = textractClient.getDocumentAnalysis(nextRequest);
      nextToken = nextResult.getNextToken();
      allBlocks.addAll(nextResult.getBlocks());
      lastResult = nextResult;
    }

    lastResult.setBlocks(allBlocks);
    return lastResult;
  }

  private GetDocumentAnalysisResult pollUntilComplete(String jobId, AmazonTextract textractClient)
      throws InterruptedException {

    final long INITIAL_DELAY_MS = 5000;
    final long MAX_DELAY_MS = 30 * 60 * 1000;
    final int MAX_RETRIES = 20;

    long delay = INITIAL_DELAY_MS;

    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      LOGGER.debug("Polling job {} attempt {} (waiting {}ms)", jobId, attempt, delay);
      Thread.sleep(delay);

      GetDocumentAnalysisResult response =
          textractClient.getDocumentAnalysis(
              new GetDocumentAnalysisRequest().withJobId(jobId).withMaxResults(MAX_RESULT));

      String status = response.getJobStatus();

      if (JobStatus.SUCCEEDED.toString().equals(status)) {
        LOGGER.info("Job {} succeeded after {} attempts", jobId, attempt);
        return response;
      } else if (JobStatus.FAILED.toString().equals(status)) {
        throw new ConnectorInputException("Textract job failed: " + response);
      }

      LOGGER.debug("Job {} still in progress ({})", jobId, status);
      delay = Math.min(delay * 2, MAX_DELAY_MS);
    }

    throw new ConnectorInputException(
        "Textract job did not complete after " + MAX_RETRIES + " attempts");
  }
}
