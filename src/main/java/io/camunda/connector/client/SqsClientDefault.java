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
package io.camunda.connector.client;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqsClientDefault implements SqsClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(SqsClientDefault.class);

  private AmazonSQS client;
  private SendMessageRequest sendMessageRequest;

  @Override
  public void init(String accessKey, String secretKey, String region) {
    LOGGER.debug("Creating aws sqs client for queue region {}", region);
    client =
        AmazonSQSClientBuilder.standard()
            .withCredentials(
                new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
            .withRegion(region)
            .build();
    LOGGER.debug("Aws sqs client was successfully created");
  }

  @Override
  public void createMsg(String url, String msgData) {
    sendMessageRequest = new SendMessageRequest().withQueueUrl(url).withMessageBody(msgData);
    LOGGER.debug("Sqs message was successfully created for queue Url [{}]", url);
  }

  @Override
  public SendMessageResult execute() {
    final var sendMessageResult = client.sendMessage(sendMessageRequest);
    LOGGER.debug("sqs message was sent, msg id : [{}]", sendMessageResult.getMessageId());
    return sendMessageResult;
  }

  @Override
  public void shutDown() {
    if (client != null) {
      client.shutdown();
    }
    sendMessageRequest = null;
    client = null;
    LOGGER.debug("Sqs client was shutdown");
  }

  public AmazonSQS getClient() {
    return client;
  }

  public void setClient(AmazonSQS client) {
    this.client = client;
  }

  public SendMessageRequest getSendMessageRequest() {
    return sendMessageRequest;
  }

  public void setSendMessageRequest(SendMessageRequest sendMessageRequest) {
    this.sendMessageRequest = sendMessageRequest;
  }
}
