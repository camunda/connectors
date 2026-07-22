/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.caller;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.textract.model.TextractRequestData;
import io.camunda.connector.textract.model.result.GetDocumentAnalysisResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.textract.TextractAsyncClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.GetDocumentAnalysisRequest;
import software.amazon.awssdk.services.textract.model.GetDocumentAnalysisResponse;
import software.amazon.awssdk.services.textract.model.JobStatus;
import software.amazon.awssdk.services.textract.model.StartDocumentAnalysisRequest;
import software.amazon.awssdk.services.textract.model.StartDocumentAnalysisResponse;

public class PollingTextractCaller
    implements TextractCaller<GetDocumentAnalysisResult, TextractAsyncClient> {

  public static final int MAX_RESULT = 1000;

  private static final int DEFAULT_MAX_ATTEMPTS = 10;
  private static final Duration DEFAULT_POLL_DELAY = Duration.ofMinutes(1);

  private static final Logger LOGGER = LoggerFactory.getLogger(PollingTextractCaller.class);

  private final int maxAttempts;
  private final Duration pollDelay;

  public PollingTextractCaller() {
    this(DEFAULT_MAX_ATTEMPTS, DEFAULT_POLL_DELAY);
  }

  /**
   * Visible for tests: lets the "polling window exhausted while still IN_PROGRESS" path (see {@link
   * #call}) be exercised without waiting out the real {@code maxAttempts * pollDelay} window (~9
   * minutes with the production defaults).
   */
  PollingTextractCaller(int maxAttempts, Duration pollDelay) {
    this.maxAttempts = maxAttempts;
    this.pollDelay = pollDelay;
  }

  @Override
  public GetDocumentAnalysisResult call(
      TextractRequestData requestData, TextractAsyncClient textractClient) throws Exception {

    final StartDocumentAnalysisRequest startDocReq =
        StartDocumentAnalysisRequest.builder()
            .featureTypesWithStrings(this.prepareFeatureTypes(requestData))
            .queriesConfig(prepareQueryConfig(requestData))
            .documentLocation(this.prepareDocumentLocation(requestData))
            .build();

    final StartDocumentAnalysisResponse result =
        textractClient.startDocumentAnalysis(startDocReq).join();
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

      GetDocumentAnalysisResponse nextResult =
          textractClient.getDocumentAnalysis(nextRequest).join();
      nextToken = nextResult.nextToken();
      allBlocks.addAll(nextResult.blocks());
      lastResult = nextResult;
    }

    // v2 responses are immutable (no setters); rebuild the last-page response with the merged
    // block list instead of v1's `lastResult.setBlocks(allBlocks)`. This intentionally preserves
    // the pre-existing (and golden-test-pinned) quirk that the merged result's
    // sdkResponseMetadata/sdkHttpMetadata/nextToken reflect only the LAST page polled, not the
    // first.
    GetDocumentAnalysisResponse mergedResult = lastResult.toBuilder().blocks(allBlocks).build();
    return GetDocumentAnalysisResult.from(mergedResult);
  }

  private GetDocumentAnalysisResponse pollUntilComplete(
      String jobId, TextractAsyncClient textractClient) {

    RetryPolicy<GetDocumentAnalysisResponse> retryPolicy =
        RetryPolicy.<GetDocumentAnalysisResponse>builder()
            .handle(Exception.class)
            // Defect fix: a FAILED job status is signalled below as a ConnectorInputException.
            // Without excluding it here, `.handle(Exception.class)` would also catch and retry
            // it up to maxAttempts times, wasting the full polling window (~9 minutes with the
            // production defaults) retrying a job that will never succeed. Genuine
            // transient/technical errors (e.g. wrapped in CompletionException by `.join()`) are
            // still retried as before.
            .abortOn(ConnectorInputException.class)
            .handleResultIf(Objects::isNull)
            .withDelay(pollDelay)
            .withMaxAttempts(maxAttempts)
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

    GetDocumentAnalysisResponse response =
        Failsafe.with(retryPolicy)
            .get(
                () -> {
                  LOGGER.debug("Textract polling job {} started", jobId);
                  GetDocumentAnalysisResponse polled =
                      textractClient
                          .getDocumentAnalysis(
                              GetDocumentAnalysisRequest.builder()
                                  .jobId(jobId)
                                  .maxResults(MAX_RESULT)
                                  .build())
                          .join();

                  JobStatus status = polled.jobStatus();

                  if (status == JobStatus.SUCCEEDED) {
                    return polled;
                  } else if (status == JobStatus.FAILED) {
                    throw new ConnectorInputException("Textract polling job: " + polled);
                  }
                  return null;
                });

    // Defect fix: when every attempt is exhausted while the job is still IN_PROGRESS,
    // `handleResultIf(Objects::isNull)` matched a RESULT (not an exception) on every attempt, so
    // Failsafe.get() returns null instead of throwing. The caller then dereferenced that null
    // (`firstResult.getBlocks()`), producing an NPE. Fail with a clear, typed exception instead.
    if (response == null) {
      throw new ConnectorException(
          "Textract job "
              + jobId
              + " did not complete within the polling window ("
              + maxAttempts
              + " attempts, "
              + pollDelay
              + " delay each)");
    }
    return response;
  }
}
