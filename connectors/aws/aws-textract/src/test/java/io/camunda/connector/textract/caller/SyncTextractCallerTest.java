/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.caller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.textract.model.TextractExecutionType;
import io.camunda.connector.textract.model.TextractRequestData;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.AnalyzeDocumentRequest;
import software.amazon.awssdk.services.textract.model.AnalyzeDocumentResponse;

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

    TextractClient textractClient = Mockito.mock(TextractClient.class);

    when(textractClient.analyzeDocument(any(AnalyzeDocumentRequest.class)))
        .thenReturn(AnalyzeDocumentResponse.builder().build());

    new SyncTextractCaller().call(requestData, textractClient);

    verify(textractClient).analyzeDocument(any(AnalyzeDocumentRequest.class));
  }
}
