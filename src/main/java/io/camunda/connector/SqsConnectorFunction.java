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

import com.amazonaws.services.sqs.model.SendMessageResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.ConnectorFunction;
import io.camunda.connector.api.Validator;
import io.camunda.connector.client.SqsClient;
import io.camunda.connector.client.SqsClientDefault;
import io.camunda.connector.model.SqsConnectorRequest;
import io.camunda.connector.model.SqsConnectorResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqsConnectorFunction implements ConnectorFunction {
  private static final Logger LOGGER = LoggerFactory.getLogger(SqsConnectorFunction.class);
  private static final Gson GSON = new GsonBuilder().create();
  private final SqsClient sqsClient;

  public SqsConnectorFunction() {
    this.sqsClient = new SqsClientDefault();
  }

  public SqsConnectorFunction(SqsClient sqsClient) {
    this.sqsClient = sqsClient;
  }

  @Override
  public Object execute(ConnectorContext context) {
    final var variables = context.getVariables();
    LOGGER.debug("Executing SQS connector with variables : {}", variables);
    final var request = GSON.fromJson(variables, SqsConnectorRequest.class);
    validate(request);
    request.replaceSecrets(context.getSecretStore());
    return new SqsConnectorResult(sendMsgToSqs(request).getMessageId());
  }

  private void validate(SqsConnectorRequest request) {
    final var validator = new Validator();
    request.validateWith(validator);
    validator.evaluate();
  }

  private SendMessageResult sendMsgToSqs(SqsConnectorRequest request) {
    try {
      sqsClient.init(request.getAccessKey(), request.getSecretKey(), request.getQueueRegion());
      sqsClient.createMsg(request.getQueueUrl(), request.getMessageBody());
      return sqsClient.execute();
    } finally {
      sqsClient.shutDown();
    }
  }
}
