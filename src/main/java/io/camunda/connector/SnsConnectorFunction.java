/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.google.gson.Gson;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.model.SnsConnectorRequest;
import io.camunda.connector.model.SnsConnectorResult;
import io.camunda.connector.suppliers.GsonComponentSupplier;
import io.camunda.connector.suppliers.SnsClientSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "AWSSNS",
    inputVariables = {"authentication", "topic"},
    type = "io.camunda:aws-sns:1")
public class SnsConnectorFunction implements OutboundConnectorFunction {
  private static final Logger LOGGER = LoggerFactory.getLogger(SnsConnectorFunction.class);

  private final SnsClientSupplier snsClientSupplier;
  private final Gson gson;

  public SnsConnectorFunction() {
    this(new SnsClientSupplier(), GsonComponentSupplier.gsonInstance());
  }

  public SnsConnectorFunction(final SnsClientSupplier snsClientSupplier, final Gson gson) {
    this.snsClientSupplier = snsClientSupplier;
    this.gson = gson;
  }

  @Override
  public Object execute(final OutboundConnectorContext context) {
    final var variables = context.getVariables();
    LOGGER.debug("Executing SNS connector with variables : {}", variables);
    final var request = gson.fromJson(variables, SnsConnectorRequest.class);
    context.validate(request);
    context.replaceSecrets(request);
    return new SnsConnectorResult(sendMsgToSns(request).getMessageId());
  }

  private PublishResult sendMsgToSns(SnsConnectorRequest request) {
    AmazonSNS snsClient = null;
    try {
      snsClient =
          snsClientSupplier.snsClient(
              request.getAuthentication().getAccessKey(),
              request.getAuthentication().getSecretKey(),
              request.getTopic().getRegion());
      PublishRequest message =
          new PublishRequest()
              .withTopicArn(request.getTopic().getTopicArn())
              .withMessage(request.getTopic().getMessage().toString())
              .withMessageAttributes(request.getTopic().getMessageAttributes())
              .withSubject(request.getTopic().getSubject());
      return snsClient.publish(message);
    } finally {
      if (snsClient != null) {
        snsClient.shutdown();
      }
    }
  }
}
