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

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.google.gson.Gson;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.model.SqsConnectorRequest;
import io.camunda.connector.model.SqsConnectorResult;
import io.camunda.connector.suppliers.GsonComponentSupplier;
import io.camunda.connector.suppliers.SqsClientSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqsConnectorFunction implements OutboundConnectorFunction {
  private static final Logger LOGGER = LoggerFactory.getLogger(SqsConnectorFunction.class);

  private final SqsClientSupplier sqsClientSupplier;
  private final Gson gson;

  public SqsConnectorFunction() {
    this(new SqsClientSupplier(), GsonComponentSupplier.gsonInstance());
  }

  public SqsConnectorFunction(final SqsClientSupplier sqsClientSupplier, final Gson gson) {
    this.sqsClientSupplier = sqsClientSupplier;
    this.gson = gson;
  }

  @Override
  public Object execute(final OutboundConnectorContext context) {
    final var variables = context.getVariables();
    LOGGER.debug("Executing SQS connector with variables : {}", variables);
    final var request = gson.fromJson(variables, SqsConnectorRequest.class);
    context.validate(request);
    context.replaceSecrets(request);
    return new SqsConnectorResult(sendMsgToSqs(request).getMessageId());
  }

  private SendMessageResult sendMsgToSqs(SqsConnectorRequest request) {
    AmazonSQS sqsClient = null;
    try {
      sqsClient =
          sqsClientSupplier.sqsClient(
              request.getAuthentication().getAccessKey(),
              request.getAuthentication().getSecretKey(),
              request.getQueue().getRegion());
      SendMessageRequest message =
          new SendMessageRequest()
              .withQueueUrl(request.getQueue().getUrl())
              .withMessageBody(request.getQueue().getMessageBody().toString())
              .withMessageAttributes(request.getQueue().getMessageAttributes());
      return sqsClient.sendMessage(message);
    } finally {
      if (sqsClient != null) {
        sqsClient.shutdown();
      }
    }
  }
}
