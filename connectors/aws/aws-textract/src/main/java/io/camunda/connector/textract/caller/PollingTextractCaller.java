/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.caller;

import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.model.*;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.textract.model.TextractRequestData;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    final int MAX_RETRIES = 10;

    RetryPolicy<GetDocumentAnalysisResult> retryPolicy =
        RetryPolicy.<GetDocumentAnalysisResult>builder()
            .handle(Exception.class)
            .handleResultIf(Objects::isNull)
            .withDelay(Duration.ofMinutes(1))
            .withMaxAttempts(MAX_RETRIES)
            .onRetry(
                ev ->
                    LOGGER.debug(
                        "Polling Textract job {} attempt {} started.", jobId, ev.getAttemptCount()))
            .onFailure(
                ev ->
                    LOGGER.warn(
                        "Textract job {} not completed after {} attempts.",
                        jobId,
                        ev.getAttemptCount()))
            .build();

    return Failsafe.with(retryPolicy)
        .get(
            () -> {
              LOGGER.debug("Textract polling job {} started", jobId);
              GetDocumentAnalysisResult response =
                  textractClient.getDocumentAnalysis(
                      new GetDocumentAnalysisRequest().withJobId(jobId).withMaxResults(MAX_RESULT));

              String status = response.getJobStatus();

              if (JobStatus.SUCCEEDED.toString().equals(status)) {
                return response;
              } else if (JobStatus.FAILED.toString().equals(status)) {
                throw new ConnectorInputException("Textract polling job: " + response);
              }
              return null;
            });
  }
}
