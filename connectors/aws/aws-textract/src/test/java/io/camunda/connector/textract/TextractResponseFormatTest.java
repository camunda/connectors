/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.api.document.DocumentReturn;
import io.camunda.connector.api.document.DocumentReturnChoice;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.textract.caller.AsyncTextractCaller;
import io.camunda.connector.textract.caller.PollingTextractCaller;
import io.camunda.connector.textract.caller.SyncTextractCaller;
import io.camunda.connector.textract.model.result.AnalyzeDocumentResult;
import io.camunda.connector.textract.model.result.DocumentMetadata;
import io.camunda.connector.textract.suppliers.AmazonTextractClientSupplier;
import io.camunda.connector.textract.util.TextractTestUtils;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TextractResponseFormatTest {

  private static final ObjectMapper MAPPER = ConnectorsObjectMapperSupplier.getCopy();

  private static final AnalyzeDocumentResult ANALYSIS =
      new AnalyzeDocumentResult(null, null, new DocumentMetadata(3), null, null, "1.0");

  @Mock private SyncTextractCaller syncCaller;
  @Mock private PollingTextractCaller pollingCaller;
  @Mock private AsyncTextractCaller asyncCaller;
  @Mock private AmazonTextractClientSupplier clientSupplier;

  @InjectMocks private TextractConnectorFunction textractConnectorFunction;

  @Test
  void absentChoice_returnsTypedResultUnchanged() throws Exception {
    var context = contextWith(null);
    stubSyncCall();

    var result = textractConnectorFunction.execute(context);

    assertThat(result).isSameAs(ANALYSIS);
  }

  @Test
  void jsonChoice_returnsTypedResultUnchanged() throws Exception {
    var context = contextWith(DocumentReturnChoice.JSON);
    stubSyncCall();

    var result = textractConnectorFunction.execute(context);

    assertThat(result).isSameAs(ANALYSIS);
  }

  @Test
  void documentChoice_returnsDocumentReturnCarryingTheSerializedAnalysis() throws Exception {
    var context = contextWith(DocumentReturnChoice.DOCUMENT);
    stubSyncCall();

    var result = textractConnectorFunction.execute(context);

    assertThat(result).isInstanceOf(DocumentReturn.class);
    var documentReturn = (DocumentReturn<?>) result;
    assertThat(documentReturn.payload().contentType()).isEqualTo("application/json");
    assertThat(documentReturn.payload().fileName()).isNull();

    String payload =
        new String(documentReturn.payload().stream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(MAPPER.readTree(payload)).isEqualTo(MAPPER.valueToTree(ANALYSIS));
  }

  @Test
  void documentChoice_wrapReturnsTheConvertedDocumentAsTheWholeResult() throws Exception {
    var context = contextWith(DocumentReturnChoice.DOCUMENT);
    stubSyncCall();

    var documentReturn = (DocumentReturn<?>) textractConnectorFunction.execute(context);
    Object converted = new Object();

    assertThat(documentReturn.wrap().apply(converted, DocumentReturnChoice.DOCUMENT))
        .isSameAs(converted);
  }

  private void stubSyncCall() throws Exception {
    when(clientSupplier.getSyncTextractClient(any())).thenCallRealMethod();
    when(syncCaller.call(any(), any())).thenReturn(ANALYSIS);
  }

  private OutboundConnectorContext contextWith(DocumentReturnChoice choice) {
    return OutboundConnectorContextBuilder.create()
        .secret("ACCESS_KEY", TextractTestUtils.ACTUAL_ACCESS_KEY)
        .secret("SECRET_KEY", TextractTestUtils.ACTUAL_SECRET_KEY)
        .variables(variablesWithChoice(choice))
        .build();
  }

  private static String variablesWithChoice(DocumentReturnChoice choice) {
    try {
      ObjectNode variables = (ObjectNode) MAPPER.readTree(TextractTestUtils.SYNC_EXECUTION_JSON);
      if (choice != null) {
        variables.putObject("documentReturnFormat").put("choice", choice.name());
      }
      return MAPPER.writeValueAsString(variables);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
