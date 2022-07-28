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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import io.camunda.connector.client.SqsClientDefault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class SqsClientDefaultTest extends BaseTest {

  private SqsClientDefault sqsClient;

  @BeforeEach
  public void init() {
    sqsClient = new SqsClientDefault();
  }

  @Test
  public void init_shouldCreateNewAmazonSqsClient() {
    sqsClient.init(ACCESS_KEY, SECRET_KEY, ACTUAL_QUEUE_REGION);
    assertNotNull(sqsClient.getClient());
  }

  @Test
  public void createMsg_shouldCreateNewSendMsgRequest() {
    // Given
    sqsClient.createMsg(ACTUAL_QUEUE_URL, SQS_MESSAGE_BODY);
    // When
    SendMessageRequest sendMessageRequest = sqsClient.getSendMessageRequest();
    // Then
    assertNotNull(sendMessageRequest);
    assertEquals(SQS_MESSAGE_BODY, sendMessageRequest.getMessageBody());
    assertEquals(ACTUAL_QUEUE_URL, sendMessageRequest.getQueueUrl());
  }

  @Test
  public void execute_shouldUseInnerSendMsgRequest() {
    // Given
    AmazonSQS client = Mockito.mock(AmazonSQS.class);
    sqsClient.setClient(client);
    sqsClient.createMsg(ACTUAL_QUEUE_URL, SQS_MESSAGE_BODY);
    SendMessageRequest sendMessageRequest = sqsClient.getSendMessageRequest();
    SendMessageResult result = new SendMessageResult();
    result.setMessageId(MSG_ID);
    Mockito.when(client.sendMessage(sendMessageRequest)).thenReturn(result);
    // When
    SendMessageResult execute = sqsClient.execute();
    // Then
    assertEquals(MSG_ID, execute.getMessageId());
  }

  @Test
  public void shutdown_shouldShutDownSqsClientAndDeleteAllData() {
    // Given
    sqsClient.init(ACCESS_KEY, SECRET_KEY, ACTUAL_QUEUE_REGION);
    sqsClient.createMsg(ACTUAL_QUEUE_URL, SQS_MESSAGE_BODY);
    assertNotNull(sqsClient.getClient());
    assertNotNull(sqsClient.getSendMessageRequest());
    // When
    sqsClient.shutDown();
    // Then
    assertNull(sqsClient.getClient());
    assertNull(sqsClient.getSendMessageRequest());
  }
}
