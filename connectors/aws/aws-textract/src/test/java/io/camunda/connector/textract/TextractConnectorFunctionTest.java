/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract;

import static io.camunda.connector.textract.model.TextractRequestData.WRONG_OUTPUT_VALUES_MSG;
import static io.camunda.connector.textract.util.TextractTestUtils.ASYNC_EXECUTION_JSON_WITH_ROLE_ARN_AND_WITHOUT_SNS_TOPIC;
import static io.camunda.connector.textract.util.TextractTestUtils.ASYNC_EXECUTION_JSON_WITH_SNS_TOPIC_AND_WITHOUT_ROLE_ARN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.amazonaws.services.textract.model.AnalyzeDocumentResult;
import com.amazonaws.services.textract.model.GetDocumentAnalysisResult;
import com.amazonaws.services.textract.model.StartDocumentAnalysisResult;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.textract.caller.AsyncTextractCaller;
import io.camunda.connector.textract.caller.PollingTextractCalller;
import io.camunda.connector.textract.caller.SyncTextractCaller;
import io.camunda.connector.textract.suppliers.AmazonTextractClientSupplier;
import io.camunda.connector.textract.util.TextractTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TextractConnectorFunctionTest {

  @Mock private SyncTextractCaller syncCaller;
  @Mock private PollingTextractCalller pollingCaller;
  @Mock private AsyncTextractCaller asyncCaller;

  @Mock private AmazonTextractClientSupplier clientSupplier;

  @InjectMocks private TextractConnectorFunction textractConnectorFunction;

  @Test
  void executeSyncReq() throws Exception {
    var outBounderContext = prepareConnectorContext(TextractTestUtils.SYNC_EXECUTION_JSON);

    when(clientSupplier.getSyncTextractClient(any())).thenCallRealMethod();
    when(syncCaller.call(any(), any())).thenReturn(new AnalyzeDocumentResult());

    var result = textractConnectorFunction.execute(outBounderContext);
    assertThat(result).isInstanceOf(AnalyzeDocumentResult.class);
  }

  @Test
  void executeAsyncReq() throws Exception {
    var outBounderContext = prepareConnectorContext(TextractTestUtils.ASYNC_EXECUTION_JSON);

    when(clientSupplier.getAsyncTextractClient(any())).thenCallRealMethod();
    when(asyncCaller.call(any(), any())).thenReturn(new StartDocumentAnalysisResult());

    var result = textractConnectorFunction.execute(outBounderContext);
    assertThat(result).isInstanceOf(StartDocumentAnalysisResult.class);
  }

  @Test
  void executePollingReq() throws Exception {
    var outBounderContext = prepareConnectorContext(TextractTestUtils.POLLING_EXECUTION_JSON);

    when(clientSupplier.getAsyncTextractClient(any())).thenCallRealMethod();
    when(pollingCaller.call(any(), any())).thenReturn(new GetDocumentAnalysisResult());

    var result = textractConnectorFunction.execute(outBounderContext);
    assertThat(result).isInstanceOf(GetDocumentAnalysisResult.class);
  }

  @Test
  void executeAsyncReqWithS3PrefixAndWithoutS3Bucket() {
    var outBounderContext =
        prepareConnectorContext(TextractTestUtils.ASYNC_EXECUTION_JSON_WITHOUT_S3_BUCKET_OUTPUT);

    Exception exception =
        assertThrows(
            ConnectorInputException.class,
            () -> textractConnectorFunction.execute(outBounderContext));

    assertThat(exception).hasMessageContaining(WRONG_OUTPUT_VALUES_MSG);
    assertThat(exception).isInstanceOf(ConnectorInputException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        ASYNC_EXECUTION_JSON_WITH_ROLE_ARN_AND_WITHOUT_SNS_TOPIC,
        ASYNC_EXECUTION_JSON_WITH_SNS_TOPIC_AND_WITHOUT_ROLE_ARN
      })
  void executeAsyncReqWithWrongNotificationData(String input) {
    var outBounderContext =
        OutboundConnectorContextBuilder.create()
            .secret("ACCESS_KEY", TextractTestUtils.ACTUAL_ACCESS_KEY)
            .secret("SECRET_KEY", TextractTestUtils.ACTUAL_SECRET_KEY)
            .variables(input)
            .build();

    Exception exception =
        assertThrows(
            ConnectorInputException.class,
            () -> textractConnectorFunction.execute(outBounderContext));

    assertThat(exception).isInstanceOf(ConnectorInputException.class);
  }

  private OutboundConnectorContextBuilder.TestConnectorContext prepareConnectorContext(
      String json) {
    return OutboundConnectorContextBuilder.create()
        .secret("ACCESS_KEY", TextractTestUtils.ACTUAL_ACCESS_KEY)
        .secret("SECRET_KEY", TextractTestUtils.ACTUAL_SECRET_KEY)
        .variables(json)
        .build();
  }
}
