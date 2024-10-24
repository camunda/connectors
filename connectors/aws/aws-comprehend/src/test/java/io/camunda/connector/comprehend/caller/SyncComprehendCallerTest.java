/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.caller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.comprehend.AmazonComprehendClient;
import com.amazonaws.services.comprehend.model.ClassifyDocumentRequest;
import com.amazonaws.services.comprehend.model.ClassifyDocumentResult;
import io.camunda.connector.comprehend.model.ComprehendSyncRequestData;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SyncComprehendCallerTest {

  private final SyncComprehendCaller syncCaller = new SyncComprehendCaller();

  @Test
  void callWithNonEmptyReadConf() {
    var syncRequest = new ComprehendSyncRequestData("text", "arn::");

    var expectedClassifyDocumentRequest =
        new ClassifyDocumentRequest()
            .withText(syncRequest.text())
            .withEndpointArn(syncRequest.endpointArn());

    AmazonComprehendClient syncClient = Mockito.mock(AmazonComprehendClient.class);
    when(syncClient.classifyDocument(expectedClassifyDocumentRequest))
        .thenReturn(new ClassifyDocumentResult());

    syncCaller.call(syncClient, syncRequest);

    verify(syncClient).classifyDocument(expectedClassifyDocumentRequest);
  }
}
