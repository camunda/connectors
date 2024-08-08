/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.caller;

import static io.camunda.connector.textract.util.TextractTestUtils.FULL_FILLED_ASYNC_TEXTRACT_DATA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.amazonaws.services.textract.AmazonTextractAsyncClient;
import com.amazonaws.services.textract.model.GetDocumentAnalysisRequest;
import com.amazonaws.services.textract.model.GetDocumentAnalysisResult;
import com.amazonaws.services.textract.model.StartDocumentAnalysisResult;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PollingTextractCalllerTest {

  private static Stream<Arguments> provideStatuses() {
    return Stream.of(
        Arguments.of("IN_PROGRESS", "SUCCEEDED"),
        Arguments.of("IN_PROGRESS", "FAILED"),
        Arguments.of("IN_PROGRESS", "PARTIAL_SUCCESS"));
  }

  @ParameterizedTest
  @MethodSource("provideStatuses")
  void callUntilSucceedOrFailedResult(String firstCallStatus, String secondCallStatus)
      throws Exception {
    AmazonTextractAsyncClient asyncClient = Mockito.mock(AmazonTextractAsyncClient.class);
    StartDocumentAnalysisResult startDocRequest = new StartDocumentAnalysisResult();
    when(asyncClient.startDocumentAnalysis(any())).thenReturn(startDocRequest);

    GetDocumentAnalysisResult mockResult1 =
        new GetDocumentAnalysisResult().withJobStatus(firstCallStatus);
    GetDocumentAnalysisResult mockResult2 =
        new GetDocumentAnalysisResult().withJobStatus(secondCallStatus);
    when(asyncClient.getDocumentAnalysis(any(GetDocumentAnalysisRequest.class)))
        .thenReturn(mockResult1, mockResult2);

    new PollingTextractCalller().call(FULL_FILLED_ASYNC_TEXTRACT_DATA, asyncClient);

    verify(asyncClient, times(2)).getDocumentAnalysis(any(GetDocumentAnalysisRequest.class));
  }
}
