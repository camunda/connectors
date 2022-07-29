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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.sqs.model.SendMessageResult;
import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.client.SqsClient;
import io.camunda.connector.model.SqsConnectorRequest;
import io.camunda.connector.model.SqsConnectorResult;
import io.camunda.connector.test.ConnectorContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class SqsConnectorFunctionTest extends BaseTest {

  private SqsConnectorFunction connector;
  private ConnectorContext context;
  private SendMessageResult sendMessageResult;
  private SqsClient mockSqsClient;

  @BeforeEach
  public void init() {
    mockSqsClient = mock(SqsClient.class);

    SqsConnectorRequest request = new SqsConnectorRequest();
    request.setQueueUrl(ACTUAL_QUEUE_URL);
    request.setQueueRegion(ACTUAL_QUEUE_REGION);
    request.setAccessKey(SECRETS + ACCESS_KEY);
    request.setSecretKey(SECRETS + SECRET_KEY);
    request.setMessageBody(SQS_MESSAGE_BODY);

    context =
        ConnectorContextBuilder.create()
            .secret(ACCESS_KEY, ACTUAL_ACCESS_KEY)
            .secret(SECRET_KEY, ACTUAL_SECRET_KEY)
            .variables(GSON.toJson(request))
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
    connector = new SqsConnectorFunction(mockSqsClient);
    // When
    when(mockSqsClient.execute()).thenReturn(sendMessageResult);
    Object execute = connector.execute(context);
    // Then
    Mockito.verify(mockSqsClient).init(ACTUAL_ACCESS_KEY, ACTUAL_SECRET_KEY, ACTUAL_QUEUE_REGION);
    Mockito.verify(mockSqsClient).createMsg(ACTUAL_QUEUE_URL, SQS_MESSAGE_BODY);
    Mockito.verify(mockSqsClient, times(1)).execute();
    Mockito.verify(mockSqsClient, times(1)).shutDown();

    assertTrue(execute instanceof SqsConnectorResult);
    var result = (SqsConnectorResult) execute;
    assertEquals(result.getMessageId(), MSG_ID);
  }
}
