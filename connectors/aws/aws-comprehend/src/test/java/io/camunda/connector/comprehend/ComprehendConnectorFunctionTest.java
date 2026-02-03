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
import software.amazon.awssdk.services.comprehend.ComprehendAsyncClient;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.ClassifyDocumentRequest;
import software.amazon.awssdk.services.comprehend.model.ClassifyDocumentResponse;
import software.amazon.awssdk.services.comprehend.model.StartDocumentClassificationJobRequest;
import software.amazon.awssdk.services.comprehend.model.StartDocumentClassificationJobResponse;

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

    ComprehendClient syncClient = Mockito.mock(ComprehendClient.class);
    when(syncClient.classifyDocument(any(ClassifyDocumentRequest.class)))
        .thenReturn(ClassifyDocumentResponse.builder()
        .build());

    when(clientSupplier.getSyncClient(any(ComprehendRequest.class))).thenReturn(syncClient);

    var result = comprehendConnectorFunction.execute(outBounderContext);
    assertThat(result).isInstanceOf(ClassifyDocumentResponse.class);
  }

  @Test
  void executeAsyncRequest() {
    var outBounderContext = prepareConnectorContext(ComprehendTestUtils.ASYNC_EXECUTION_JSON);

    ComprehendAsyncClient asyncClient = Mockito.mock(ComprehendAsyncClient.class);
    when(asyncClient.startDocumentClassificationJob(
            any(StartDocumentClassificationJobRequest.class)))
        .thenReturn(StartDocumentClassificationJobResponse.builder()
        .build());

    when(clientSupplier.getAsyncClient(any(ComprehendRequest.class))).thenReturn(asyncClient);

    var result = comprehendConnectorFunction.execute(outBounderContext);
    assertThat(result).isInstanceOf(StartDocumentClassificationJobResponse.class);
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
