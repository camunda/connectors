/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.caller;

import static com.amazonaws.services.textract.model.FeatureType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.amazonaws.services.textract.AmazonTextractClient;
import com.amazonaws.services.textract.model.AnalyzeDocumentRequest;
import com.amazonaws.services.textract.model.AnalyzeDocumentResult;
import io.camunda.connector.textract.model.DocumentLocationType;
import io.camunda.connector.textract.model.TextractExecutionType;
import io.camunda.connector.textract.model.TextractRequestData;
import io.camunda.connector.api.document.Document;
import java.nio.ByteBuffer;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SyncTextractCallerTest {
  @Test
  void callWithS3DocumentLocation() {
    TextractRequestData requestData =
        new TextractRequestData(
            TextractExecutionType.SYNC,
            DocumentLocationType.S3,
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
            "prefix",
            null);

    AmazonTextractClient textractClient = mock(AmazonTextractClient.class);

    when(textractClient.analyzeDocument(any(AnalyzeDocumentRequest.class)))
        .thenReturn(new AnalyzeDocumentResult());

    new SyncTextractCaller().call(requestData, textractClient);

    verify(textractClient).analyzeDocument(any(AnalyzeDocumentRequest.class));
  }

  @Test
  void callWithUploadDocumentLocation() {
    final Document document = mock(Document.class);
    byte[] bytes = HexFormat.of().parseHex("e04fd020ea3a6910a2d808002b30309d");

    when(document.asByteArray()).thenReturn(bytes);

    TextractRequestData requestData =
        new TextractRequestData(
            TextractExecutionType.SYNC,
            DocumentLocationType.UPLOADED,
            null,
            null,
            null,
            true,
            false,
            false,
            false,
            "token",
            "client-request-token",
            "job-tag",
            "notification-channel",
            "role-arn",
            "outputBucket",
            "prefix",
            document);

    AmazonTextractClient textractClient = mock(AmazonTextractClient.class);

    when(textractClient.analyzeDocument(any(AnalyzeDocumentRequest.class)))
        .thenReturn(new AnalyzeDocumentResult());

    new SyncTextractCaller().call(requestData, textractClient);

    ArgumentCaptor<AnalyzeDocumentRequest> argumentCaptor =
        ArgumentCaptor.forClass(AnalyzeDocumentRequest.class);

    verify(textractClient).analyzeDocument(argumentCaptor.capture());
    AnalyzeDocumentRequest analyzeDocumentRequest = argumentCaptor.getValue();
    assertThat(analyzeDocumentRequest)
        .isEqualTo(
            new AnalyzeDocumentRequest()
                .withFeatureTypes(TABLES.name())
                .withDocument(
                    new com.amazonaws.services.textract.model.Document()
                        .withBytes(ByteBuffer.wrap(bytes))));
  }
}
