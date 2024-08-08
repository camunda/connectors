/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.caller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.textract.AmazonTextractClient;
import com.amazonaws.services.textract.model.AnalyzeDocumentRequest;
import com.amazonaws.services.textract.model.AnalyzeDocumentResult;
import io.camunda.connector.textract.model.TextractExecutionType;
import io.camunda.connector.textract.model.TextractRequestData;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SyncTextractCallerTest {
  @Test
  void call() {
    TextractRequestData requestData =
        new TextractRequestData(
            TextractExecutionType.SYNC,
            "test-bucket",
            "test-object",
            "1",
            true,
            true,
            true,
            true,
            "token",
            "client-request-token",
            "job-tag",
            "notification-channel",
            "role-arn",
            "outputBucket",
            "prefix");

    AmazonTextractClient textractClient = Mockito.mock(AmazonTextractClient.class);

    when(textractClient.analyzeDocument(any(AnalyzeDocumentRequest.class)))
        .thenReturn(new AnalyzeDocumentResult());

    new SyncTextractCaller().call(requestData, textractClient);

    verify(textractClient).analyzeDocument(any(AnalyzeDocumentRequest.class));
  }
}
