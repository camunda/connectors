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

import java.util.List;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

@ExtendWith(MockitoExtension.class)
class PollingTextractCallerTest {

  @Test
  void callUtilDocumentAnalysisResultNextTokenEqNull() throws Exception {
    List<Pair<GetDocumentAnalysisRequest, GetDocumentAnalysisResponse>> callSequence =
        getRequestResponseSequence();
    Pair<GetDocumentAnalysisRequest, GetDocumentAnalysisResponse> firstRequestResp =
        callSequence.getFirst();

    TextractClient asyncClient = Mockito.mock(TextractClient.class);
    StartDocumentAnalysisResponse startDocRequest =
        StartDocumentAnalysisResponse.builder().jobId(firstRequestResp.getLeft().jobId()).build();
    when(asyncClient.startDocumentAnalysis(any(StartDocumentAnalysisRequest.class)))
        .thenReturn(startDocRequest);

    when(asyncClient.getDocumentAnalysis(firstRequestResp.getLeft()))
        .thenReturn(firstRequestResp.getRight());

    Pair<GetDocumentAnalysisRequest, GetDocumentAnalysisResponse> secondRequestResp =
        callSequence.getLast();

    when(asyncClient.getDocumentAnalysis(secondRequestResp.getLeft()))
        .thenReturn(secondRequestResp.getRight());

    List<Block> expectedBlocks =
        ListUtils.union(
            firstRequestResp.getRight().blocks(), secondRequestResp.getRight().blocks());

    GetDocumentAnalysisResponse result =
        new PollingTextractCaller().call(FULL_FILLED_ASYNC_TEXTRACT_DATA, asyncClient);

    verify(asyncClient).getDocumentAnalysis(firstRequestResp.getLeft());
    verify(asyncClient).getDocumentAnalysis(secondRequestResp.getLeft());

    assertThat(result.blocks()).isEqualTo(expectedBlocks);
    assertThat(result)
        .usingRecursiveComparison()
        .ignoringFields("blocks")
        .isEqualTo(secondRequestResp.getRight());
  }

  private List<Pair<GetDocumentAnalysisRequest, GetDocumentAnalysisResponse>>
      getRequestResponseSequence() {
    String jobId = "1";
    GetDocumentAnalysisRequest firstDocRequest =
        GetDocumentAnalysisRequest.builder()
            .jobId(jobId)
            .maxResults(MAX_RESULT)
            .nextToken(null)
            .build();

    String nextToken = "2";
    GetDocumentAnalysisRequest secondDocRequest =
        GetDocumentAnalysisRequest.builder()
            .jobId(jobId)
            .maxResults(MAX_RESULT)
            .nextToken(nextToken)
            .build();

    GetDocumentAnalysisResponse firstDocResult =
        GetDocumentAnalysisResponse.builder()
            .jobStatus(JobStatus.SUCCEEDED.toString())
            .nextToken(nextToken)
            .blocks(
                List.of(Block.builder().text("AAA").build(), Block.builder().text("BBB").build()))
            .build();

    GetDocumentAnalysisResponse secondDocResult =
        GetDocumentAnalysisResponse.builder()
            .jobStatus(JobStatus.SUCCEEDED.toString())
            .nextToken(null)
            .blocks(
                List.of(Block.builder().text("CCC").build(), Block.builder().text("DDD").build()))
            .build();

    return List.of(
        Pair.of(firstDocRequest, firstDocResult), Pair.of(secondDocRequest, secondDocResult));
  }
}
