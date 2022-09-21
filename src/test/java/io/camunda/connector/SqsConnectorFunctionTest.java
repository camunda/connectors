/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.model.SqsConnectorResult;
import io.camunda.connector.suppliers.SqsClientSupplier;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class SqsConnectorFunctionTest extends BaseTest {

  private SqsConnectorFunction connector;
  private OutboundConnectorContext context;
  private SendMessageResult sendMessageResult;

  @BeforeEach
  public void init() {
    context =
        OutboundConnectorContextBuilder.create()
            .secret(AWS_ACCESS_KEY, ACTUAL_ACCESS_KEY)
            .secret(AWS_SECRET_KEY, ACTUAL_SECRET_KEY)
            .variables(DEFAULT_REQUEST_BODY)
            .build();
    sendMessageResult = new SendMessageResult();
    sendMessageResult.setMessageId(MSG_ID);
  }

  @Test
  public void execute_shouldThrowExceptionWhenSQSClientNotExist() {
    // Given context with correct data and request
    connector = new SqsConnectorFunction();
    // When connector.execute(context) without amazon sqs client
    // Then we expect SdkClientException
    assertThrows(
        SdkClientException.class,
        () -> connector.execute(context),
        "SdkClientException from amazon was expected");
  }

  @Test
  public void execute_shouldExecuteRequestAndReturnResultWithMsgId() {
    // Given
    AmazonSQS sqsClient = Mockito.mock(AmazonSQS.class);
    Mockito.when(sqsClient.sendMessage(ArgumentMatchers.any(SendMessageRequest.class)))
        .thenReturn(sendMessageResult);
    SqsClientSupplier sqsClientSupplier = Mockito.mock(SqsClientSupplier.class);
    Mockito.when(
            sqsClientSupplier.sqsClient(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString()))
        .thenReturn(sqsClient);
    connector = new SqsConnectorFunction(sqsClientSupplier, GSON);

    // When
    Object execute = connector.execute(context);

    // Then
    Mockito.verify(sqsClient, Mockito.times(1)).shutdown();

    Assertions.assertThat(execute).isInstanceOf(SqsConnectorResult.class);
    var result = (SqsConnectorResult) execute;
    Assertions.assertThat(result.getMessageId()).isEqualTo(MSG_ID);
  }
}
