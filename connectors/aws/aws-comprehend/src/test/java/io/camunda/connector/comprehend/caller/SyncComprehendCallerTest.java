/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.caller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.comprehend.model.ComprehendSyncRequestData;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.ClassifyDocumentRequest;
import software.amazon.awssdk.services.comprehend.model.ClassifyDocumentResponse;

class SyncComprehendCallerTest {

  private final SyncComprehendCaller syncCaller = new SyncComprehendCaller();

  @Test
  void callWithNonEmptyReadConf() {
    var syncRequest = new ComprehendSyncRequestData("text", "arn::");

    var expectedClassifyDocumentRequest =
        ClassifyDocumentRequest.builder()
            .text(syncRequest.text())
            .endpointArn(syncRequest.endpointArn())
            .build();

    ComprehendClient syncClient = Mockito.mock(ComprehendClient.class);
    when(syncClient.classifyDocument(expectedClassifyDocumentRequest))
        .thenReturn(ClassifyDocumentResponse.builder().build());

    syncCaller.call(syncClient, syncRequest);

    verify(syncClient).classifyDocument(expectedClassifyDocumentRequest);
  }
}
