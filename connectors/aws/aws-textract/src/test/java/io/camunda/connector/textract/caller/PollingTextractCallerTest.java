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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.textract.AmazonTextractAsyncClient;
import com.amazonaws.services.textract.model.Block;
import com.amazonaws.services.textract.model.GetDocumentAnalysisRequest;
import com.amazonaws.services.textract.model.GetDocumentAnalysisResult;
import com.amazonaws.services.textract.model.JobStatus;
import com.amazonaws.services.textract.model.StartDocumentAnalysisResult;
import java.util.List;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PollingTextractCallerTest {

  @Test
  void callUtilDocumentAnalysisResultNextTokenEqNull() throws Exception {
    List<Pair<GetDocumentAnalysisRequest, GetDocumentAnalysisResult>> callSequence =
        getRequestResponseSequence();
    Pair<GetDocumentAnalysisRequest, GetDocumentAnalysisResult> firstRequestResp =
        callSequence.getFirst();

    AmazonTextractAsyncClient asyncClient = Mockito.mock(AmazonTextractAsyncClient.class);
    StartDocumentAnalysisResult startDocRequest =
        new StartDocumentAnalysisResult().withJobId(firstRequestResp.getLeft().getJobId());
    when(asyncClient.startDocumentAnalysis(any())).thenReturn(startDocRequest);

    when(asyncClient.getDocumentAnalysis(firstRequestResp.getLeft()))
        .thenReturn(firstRequestResp.getRight());

    Pair<GetDocumentAnalysisRequest, GetDocumentAnalysisResult> secondRequestResp =
        callSequence.getLast();

    when(asyncClient.getDocumentAnalysis(secondRequestResp.getLeft()))
        .thenReturn(secondRequestResp.getRight());

    List<Block> expectedBlocks =
        ListUtils.union(
            firstRequestResp.getRight().getBlocks(), secondRequestResp.getRight().getBlocks());

    GetDocumentAnalysisResult result =
        new PollingTextractCaller().call(FULL_FILLED_ASYNC_TEXTRACT_DATA, asyncClient);

    verify(asyncClient).getDocumentAnalysis(firstRequestResp.getLeft());
    verify(asyncClient).getDocumentAnalysis(secondRequestResp.getLeft());

    assertThat(result.getBlocks()).isEqualTo(expectedBlocks);
    assertThat(result)
        .usingRecursiveComparison()
        .ignoringFields("blocks")
        .isEqualTo(secondRequestResp.getRight());
  }

  private List<Pair<GetDocumentAnalysisRequest, GetDocumentAnalysisResult>>
      getRequestResponseSequence() {
    String jobId = "1";
    GetDocumentAnalysisRequest firstDocRequest =
        new GetDocumentAnalysisRequest()
            .withJobId(jobId)
            .withMaxResults(MAX_RESULT)
            .withNextToken(null);

    String nextToken = "2";
    GetDocumentAnalysisRequest secondDocRequest =
        new GetDocumentAnalysisRequest()
            .withJobId(jobId)
            .withMaxResults(MAX_RESULT)
            .withNextToken(nextToken);

    GetDocumentAnalysisResult firstDocResult =
        new GetDocumentAnalysisResult()
            .withJobStatus(JobStatus.SUCCEEDED.toString())
            .withNextToken(nextToken)
            .withBlocks(List.of(new Block().withText("AAA"), new Block().withText("BBB")));

    GetDocumentAnalysisResult secondDocResult =
        new GetDocumentAnalysisResult()
            .withJobStatus(JobStatus.SUCCEEDED.toString())
            .withNextToken(null)
            .withBlocks(List.of(new Block().withText("CCC"), new Block().withText("DDD")));

    return List.of(
        Pair.of(firstDocRequest, firstDocResult), Pair.of(secondDocRequest, secondDocResult));
  }
}
