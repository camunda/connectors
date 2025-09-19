/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.amazonaws.services.comprehend.AmazonComprehendAsyncClient;
import com.amazonaws.services.comprehend.AmazonComprehendClient;
import com.amazonaws.services.comprehend.model.ClassifyDocumentRequest;
import com.amazonaws.services.comprehend.model.ClassifyDocumentResult;
import com.amazonaws.services.comprehend.model.StartDocumentClassificationJobRequest;
import com.amazonaws.services.comprehend.model.StartDocumentClassificationJobResult;
import io.camunda.connector.comprehend.caller.AsyncComprehendCaller;
import io.camunda.connector.comprehend.caller.SyncComprehendCaller;
import io.camunda.connector.comprehend.model.ComprehendRequest;
import io.camunda.connector.comprehend.supplier.ComprehendClientSupplier;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ComprehendConnectorFunctionTest {

  @Mock private ComprehendClientSupplier clientSupplier;
  private ComprehendConnectorFunction comprehendConnectorFunction;

  @BeforeEach
  void setUp() {
    comprehendConnectorFunction =
        new ComprehendConnectorFunction(
            clientSupplier, new SyncComprehendCaller(), new AsyncComprehendCaller());
  }

  @Test
  void executeSyncRequest() {
    var outBounderContext = prepareConnectorContext(ComprehendTestUtils.SYNC_EXECUTION_JSON);

    AmazonComprehendClient syncClient = Mockito.mock(AmazonComprehendClient.class);
    when(syncClient.classifyDocument(any(ClassifyDocumentRequest.class)))
        .thenReturn(new ClassifyDocumentResult());

    when(clientSupplier.getSyncClient(any(ComprehendRequest.class))).thenReturn(syncClient);

    var result = comprehendConnectorFunction.execute(outBounderContext);
    assertThat(result).isInstanceOf(ClassifyDocumentResult.class);
  }

  @Test
  void executeAsyncRequest() {
    var outBounderContext = prepareConnectorContext(ComprehendTestUtils.ASYNC_EXECUTION_JSON);

    AmazonComprehendAsyncClient asyncClient = Mockito.mock(AmazonComprehendAsyncClient.class);
    when(asyncClient.startDocumentClassificationJob(
            any(StartDocumentClassificationJobRequest.class)))
        .thenReturn(new StartDocumentClassificationJobResult());

    when(clientSupplier.getAsyncClient(any(ComprehendRequest.class))).thenReturn(asyncClient);

    var result = comprehendConnectorFunction.execute(outBounderContext);
    assertThat(result).isInstanceOf(StartDocumentClassificationJobResult.class);
  }

  private OutboundConnectorContextBuilder.TestConnectorContext prepareConnectorContext(
      String json) {
    return OutboundConnectorContextBuilder.create()
        .secret("ACCESS_KEY", ComprehendTestUtils.ACTUAL_ACCESS_KEY)
        .secret("SECRET_KEY", ComprehendTestUtils.ACTUAL_SECRET_KEY)
        .variables(json)
        .build();
  }
}
