/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.caller;

import static io.camunda.connector.textract.caller.PollingTextractCaller.MAX_RESULT;
import static io.camunda.connector.textract.util.TextractTestUtils.FULL_FILLED_ASYNC_TEXTRACT_DATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.textract.model.result.GetDocumentAnalysisResult;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.textract.TextractAsyncClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.GetDocumentAnalysisRequest;
import software.amazon.awssdk.services.textract.model.GetDocumentAnalysisResponse;
import software.amazon.awssdk.services.textract.model.JobStatus;
import software.amazon.awssdk.services.textract.model.StartDocumentAnalysisRequest;
import software.amazon.awssdk.services.textract.model.StartDocumentAnalysisResponse;

@ExtendWith(MockitoExtension.class)
class PollingTextractCallerTest {

  @Test
  void callUtilDocumentAnalysisResultNextTokenEqNull() throws Exception {
    List<Pair<GetDocumentAnalysisRequest, GetDocumentAnalysisResponse>> callSequence =
        getRequestResponseSequence();
    Pair<GetDocumentAnalysisRequest, GetDocumentAnalysisResponse> firstRequestResp =
        callSequence.getFirst();

    TextractAsyncClient asyncClient = Mockito.mock(TextractAsyncClient.class);
    StartDocumentAnalysisResponse startDocResponse =
        StartDocumentAnalysisResponse.builder().jobId(firstRequestResp.getLeft().jobId()).build();
    when(asyncClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(startDocResponse));

    when(asyncClient.getDocumentAnalysis(firstRequestResp.getLeft()))
        .thenReturn(CompletableFuture.completedFuture(firstRequestResp.getRight()));

    Pair<GetDocumentAnalysisRequest, GetDocumentAnalysisResponse> secondRequestResp =
        callSequence.getLast();

    when(asyncClient.getDocumentAnalysis(secondRequestResp.getLeft()))
        .thenReturn(CompletableFuture.completedFuture(secondRequestResp.getRight()));

    List<Block> expectedBlocks =
        ListUtils.union(
            firstRequestResp.getRight().blocks(), secondRequestResp.getRight().blocks());

    GetDocumentAnalysisResult result =
        new PollingTextractCaller().call(FULL_FILLED_ASYNC_TEXTRACT_DATA, asyncClient);

    verify(asyncClient).getDocumentAnalysis(firstRequestResp.getLeft());
    verify(asyncClient).getDocumentAnalysis(secondRequestResp.getLeft());

    List<String> expectedBlockIds = expectedBlocks.stream().map(Block::id).toList();
    List<String> actualBlockIds =
        result.blocks().stream().map(io.camunda.connector.textract.model.result.Block::id).toList();
    assertThat(actualBlockIds).containsExactlyElementsOf(expectedBlockIds);
    // The merged result otherwise reflects the LAST page polled (see PollingTextractCaller),
    // which is the documented (golden-tested) v1 behavior being preserved across the migration.
    assertThat(result.nextToken()).isEqualTo(secondRequestResp.getRight().nextToken());
    assertThat(result.jobStatus()).isEqualTo(secondRequestResp.getRight().jobStatusAsString());
  }

  /**
   * Defect (b): a FAILED job status must fail fast (abort retries), not exhaust the whole polling
   * window retrying an outcome that will never succeed.
   */
  @Test
  void failedJobStatus_failsFastWithoutRetrying() {
    String jobId = "job-failed";
    TextractAsyncClient asyncClient = mock(TextractAsyncClient.class);
    when(asyncClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                StartDocumentAnalysisResponse.builder().jobId(jobId).build()));

    GetDocumentAnalysisResponse failedResponse =
        GetDocumentAnalysisResponse.builder()
            .jobStatus(JobStatus.FAILED)
            .statusMessage("boom")
            .build();
    when(asyncClient.getDocumentAnalysis(any(GetDocumentAnalysisRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(failedResponse));

    // maxAttempts is intentionally large here: if the abort-on-failure fix regresses, this test
    // must fail fast (wrong exception / too many invocations) rather than hang for minutes.
    PollingTextractCaller caller = new PollingTextractCaller(10, Duration.ofMillis(1));

    assertThrows(
        ConnectorInputException.class,
        () -> caller.call(FULL_FILLED_ASYNC_TEXTRACT_DATA, asyncClient));

    verify(asyncClient, times(1)).getDocumentAnalysis(any(GetDocumentAnalysisRequest.class));
  }

  /**
   * Defect (c): exhausting every polling attempt while the job is still IN_PROGRESS must throw a
   * clean, typed exception - not NPE on a null result.
   */
  @Test
  void exhaustingAttemptsWhileInProgress_throwsInsteadOfNpe() {
    String jobId = "job-in-progress";
    TextractAsyncClient asyncClient = mock(TextractAsyncClient.class);
    when(asyncClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                StartDocumentAnalysisResponse.builder().jobId(jobId).build()));

    GetDocumentAnalysisResponse inProgressResponse =
        GetDocumentAnalysisResponse.builder().jobStatus(JobStatus.IN_PROGRESS).build();
    when(asyncClient.getDocumentAnalysis(any(GetDocumentAnalysisRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(inProgressResponse));

    int maxAttempts = 2;
    PollingTextractCaller caller = new PollingTextractCaller(maxAttempts, Duration.ofMillis(1));

    ConnectorException exception =
        assertThrows(
            ConnectorException.class,
            () -> caller.call(FULL_FILLED_ASYNC_TEXTRACT_DATA, asyncClient));

    assertThat(exception.getMessage()).contains(jobId);
    verify(asyncClient, times(maxAttempts))
        .getDocumentAnalysis(any(GetDocumentAnalysisRequest.class));
  }

  /**
   * The migration requirement preserves retries for transient/technical failures (e.g. throttling
   * or network errors surfaced via {@code CompletionException} from {@code .join()}). Only a
   * genuine FAILED job status should abort retries early; any other exception must still be retried
   * up to {@code maxAttempts}.
   */
  @Test
  void transientFailureThenSuccess_retriesAndReturnsResult() throws Exception {
    String jobId = "job-transient-failure";
    TextractAsyncClient asyncClient = mock(TextractAsyncClient.class);
    when(asyncClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                StartDocumentAnalysisResponse.builder().jobId(jobId).build()));

    GetDocumentAnalysisResponse succeededResponse =
        GetDocumentAnalysisResponse.builder().jobStatus(JobStatus.SUCCEEDED).build();

    CompletableFuture<GetDocumentAnalysisResponse> transientFailure = new CompletableFuture<>();
    transientFailure.completeExceptionally(new RuntimeException("transient network error"));

    when(asyncClient.getDocumentAnalysis(any(GetDocumentAnalysisRequest.class)))
        .thenReturn(transientFailure)
        .thenReturn(CompletableFuture.completedFuture(succeededResponse));

    PollingTextractCaller caller = new PollingTextractCaller(3, Duration.ofMillis(1));

    GetDocumentAnalysisResult result = caller.call(FULL_FILLED_ASYNC_TEXTRACT_DATA, asyncClient);

    assertThat(result.jobStatus()).isEqualTo(succeededResponse.jobStatusAsString());
    verify(asyncClient, times(2)).getDocumentAnalysis(any(GetDocumentAnalysisRequest.class));
  }

  /**
   * PARTIAL_SUCCESS is also a terminal GetDocumentAnalysis status (the job finished, but some pages
   * could not be analyzed) and must be treated as complete rather than still-in-progress -
   * otherwise the connector polls the completed job until the window times out and discards the
   * partial result.
   */
  @Test
  void partialSuccessJobStatus_treatedAsComplete() throws Exception {
    String jobId = "job-partial-success";
    TextractAsyncClient asyncClient = mock(TextractAsyncClient.class);
    when(asyncClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(
            CompletableFuture.completedFuture(
                StartDocumentAnalysisResponse.builder().jobId(jobId).build()));

    GetDocumentAnalysisResponse partialSuccessResponse =
        GetDocumentAnalysisResponse.builder()
            .jobStatus(JobStatus.PARTIAL_SUCCESS)
            .blocks(Block.builder().id("block-partial").text("partial").build())
            .build();
    when(asyncClient.getDocumentAnalysis(any(GetDocumentAnalysisRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(partialSuccessResponse));

    // maxAttempts is intentionally larger than 1: if PARTIAL_SUCCESS regresses to being treated
    // as still-in-progress, this test observes more than a single poll (or ultimately a
    // ConnectorException once the window is exhausted) instead of an immediate result.
    PollingTextractCaller caller = new PollingTextractCaller(2, Duration.ofMillis(1));

    GetDocumentAnalysisResult result = caller.call(FULL_FILLED_ASYNC_TEXTRACT_DATA, asyncClient);

    assertThat(result.jobStatus()).isEqualTo(partialSuccessResponse.jobStatusAsString());
    verify(asyncClient, times(1)).getDocumentAnalysis(any(GetDocumentAnalysisRequest.class));
  }

  private List<Pair<GetDocumentAnalysisRequest, GetDocumentAnalysisResponse>>
      getRequestResponseSequence() {
    String jobId = "1";
    GetDocumentAnalysisRequest firstDocRequest =
        GetDocumentAnalysisRequest.builder().jobId(jobId).maxResults(MAX_RESULT).build();

    String nextToken = "2";
    GetDocumentAnalysisRequest secondDocRequest =
        GetDocumentAnalysisRequest.builder()
            .jobId(jobId)
            .maxResults(MAX_RESULT)
            .nextToken(nextToken)
            .build();

    GetDocumentAnalysisResponse firstDocResult =
        GetDocumentAnalysisResponse.builder()
            .jobStatus(JobStatus.SUCCEEDED)
            .nextToken(nextToken)
            .blocks(
                Block.builder().id("block-aaa").text("AAA").build(),
                Block.builder().id("block-bbb").text("BBB").build())
            .build();

    GetDocumentAnalysisResponse secondDocResult =
        GetDocumentAnalysisResponse.builder()
            .jobStatus(JobStatus.SUCCEEDED)
            .blocks(
                Block.builder().id("block-ccc").text("CCC").build(),
                Block.builder().id("block-ddd").text("DDD").build())
            .build();

    return List.of(
        Pair.of(firstDocRequest, firstDocResult), Pair.of(secondDocRequest, secondDocResult));
  }
}
