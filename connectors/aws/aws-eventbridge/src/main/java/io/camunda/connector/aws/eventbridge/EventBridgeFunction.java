/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.eventbridge;

import static io.camunda.connector.aws.AwsUtils.extractRegionOrDefault;

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
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import java.util.Optional;

@OutboundConnector(
    name = "AWS EventBridge",
    inputVariables = {"authentication", "configuration", "input"},
    type = "io.camunda:aws-eventbridge:1")
@ElementTemplate(
    id = "io.camunda.connectors.AWSEventBridge.v1",
    name = "Amazon EventBridge Outbound Connector",
    description = "Send events to AWS EventBridge",
    inputDataClass = AwsEventBridgeRequest.class,
    version = 5,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Queue properties"),
      @ElementTemplate.PropertyGroup(id = "eventDetails", label = "eventDetails"),
      @ElementTemplate.PropertyGroup(id = "eventPayload", label = "Event Payload")
    },
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-eventbridge/?awseventbridge=outbound",
    icon = "icon.svg")
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
    var eventBridgeRequest = context.bindVariables(AwsEventBridgeRequest.class);
    AmazonEventBridge amazonEventBridgeClient = createEventBridgeClient(eventBridgeRequest);
    return objectMapper.convertValue(
        putEvents(amazonEventBridgeClient, eventBridgeRequest.getInput()), Object.class);
  }

  private AmazonEventBridge createEventBridgeClient(final AwsEventBridgeRequest request) {
    Optional<String> endpoint =
        Optional.ofNullable(request.getConfiguration()).map(AwsBaseConfiguration::endpoint);
    var credentialsProvider = CredentialsProviderSupport.credentialsProvider(request);
    var region =
        extractRegionOrDefault(request.getConfiguration(), request.getConfiguration().region());
    return endpoint
        .map(
            ep ->
                awsEventBridgeClientSupplier.getAmazonEventBridgeClient(
                    credentialsProvider, region, ep))
        .orElseGet(
            () ->
                awsEventBridgeClientSupplier.getAmazonEventBridgeClient(
                    credentialsProvider, region));
  }

  public PutEventsResult putEvents(final AmazonEventBridge client, final AwsEventBridgeInput input)
      throws JsonProcessingException {
    PutEventsRequestEntry entry =
        new PutEventsRequestEntry()
            .withSource(input.getSource())
            .withDetailType(input.getDetailType())
            .withEventBusName(input.getEventBusName())
            .withDetail(objectMapper.writeValueAsString(input.getDetail()));
    return client.putEvents(new PutEventsRequest().withEntries(entry));
  }
}
