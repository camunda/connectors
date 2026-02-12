/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.caller;

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
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

public class PollingTextractCaller implements TextractCaller<GetDocumentAnalysisResponse> {

  public static final int MAX_RESULT = 1000;

  private static final Logger LOGGER = LoggerFactory.getLogger(PollingTextractCaller.class);

  @Override
  public GetDocumentAnalysisResponse call(
      TextractRequestData requestData, TextractClient textractClient) throws Exception {

    final StartDocumentAnalysisRequest startDocReq =
        StartDocumentAnalysisRequest.builder()
            .featureTypes(this.prepareFeatureTypes(requestData))
            .queriesConfig(prepareQueryConfig(requestData))
            .documentLocation(this.prepareDocumentLocation(requestData))
            .build();

    final StartDocumentAnalysisResponse result = textractClient.startDocumentAnalysis(startDocReq);
    final String jobId = result.jobId();

    LOGGER.debug("Started document analysis with jobId: {}", jobId);

    GetDocumentAnalysisResponse firstResult = pollUntilComplete(jobId, textractClient);

    List<Block> allBlocks = new ArrayList<>(firstResult.blocks());
    GetDocumentAnalysisResponse lastResult = firstResult;
    String nextToken = firstResult.nextToken();

    while (StringUtils.isNotEmpty(nextToken)) {
      GetDocumentAnalysisRequest nextRequest =
          GetDocumentAnalysisRequest.builder()
              .jobId(jobId)
              .maxResults(MAX_RESULT)
              .nextToken(nextToken)
              .build();

      GetDocumentAnalysisResponse nextResult = textractClient.getDocumentAnalysis(nextRequest);
      nextToken = nextResult.nextToken();
      allBlocks.addAll(nextResult.blocks());
      lastResult = nextResult;
    }

    lastResult = lastResult.toBuilder().blocks(allBlocks).build();
    return lastResult;
  }

  private GetDocumentAnalysisResponse pollUntilComplete(String jobId, TextractClient textractClient)
      throws InterruptedException {

    final int MAX_RETRIES = 10;

    RetryPolicy<GetDocumentAnalysisResponse> retryPolicy =
        RetryPolicy.<GetDocumentAnalysisResponse>builder()
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
              GetDocumentAnalysisResponse response =
                  textractClient.getDocumentAnalysis(
                      GetDocumentAnalysisRequest.builder()
                          .jobId(jobId)
                          .maxResults(MAX_RESULT)
                          .build());

              JobStatus status = response.jobStatus();

              if (JobStatus.SUCCEEDED.equals(status)) {
                return response;
              } else if (JobStatus.FAILED.equals(status)) {
                throw new ConnectorInputException("Textract polling job: " + response);
              }
              return null;
            });
  }
}
