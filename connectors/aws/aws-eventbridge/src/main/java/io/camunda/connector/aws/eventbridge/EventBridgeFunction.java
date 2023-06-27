/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.eventbridge;

import com.amazonaws.services.eventbridge.AmazonEventBridge;
import com.amazonaws.services.eventbridge.model.PutEventsRequest;
import com.amazonaws.services.eventbridge.model.PutEventsRequestEntry;
import com.amazonaws.services.eventbridge.model.PutEventsResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.CredentialsProviderSupport;
import io.camunda.connector.aws.ObjectMapperSupplier;

@OutboundConnector(
    name = "AWSEventBridge",
    inputVariables = {"authentication", "configuration", "input"},
    type = "io.camunda:aws-eventbridge:1")
public class EventBridgeFunction implements OutboundConnectorFunction {

  private final AwsEventBridgeClientSupplier awsEventBridgeClientSupplier;
  private final ObjectMapper objectMapper;

  public EventBridgeFunction() {
    this.awsEventBridgeClientSupplier = new AwsEventBridgeClientSupplier();
    this.objectMapper = ObjectMapperSupplier.getMapperInstance();
  }

  public EventBridgeFunction(
      final AwsEventBridgeClientSupplier clientSupplier, final ObjectMapper objectMapper) {
    this.awsEventBridgeClientSupplier = clientSupplier;
    this.objectMapper = objectMapper;
  }

  @Override
  public Object execute(OutboundConnectorContext context) throws JsonProcessingException {
    var eventBridgeRequest = context.getVariablesAsType(AwsEventBridgeRequest.class);

    context.validate(eventBridgeRequest);
    context.replaceSecrets(eventBridgeRequest);

    AmazonEventBridge amazonEventBridgeClient =
        awsEventBridgeClientSupplier.getAmazonEventBridgeClient(
            CredentialsProviderSupport.credentialsProvider(eventBridgeRequest),
            eventBridgeRequest.getConfiguration().getRegion());
    return objectMapper.convertValue(
        putEvents(amazonEventBridgeClient, eventBridgeRequest.getInput()), Object.class);
  }

  public PutEventsResult putEvents(final AmazonEventBridge client, final AwsEventBridgeInput input)
      throws JsonProcessingException {
    PutEventsRequestEntry entry =
        new PutEventsRequestEntry()
            .withSource(input.getSource())
            .withDetailType(input.getDetailType())
            .withEventBusName(input.getEventBusName())
            .withDetail(objectMapper.writeValueAsString(input.getDetail()));

    PutEventsRequest eventsRequest = new PutEventsRequest().withEntries(entry);
    return client.putEvents(eventsRequest);
  }
}
