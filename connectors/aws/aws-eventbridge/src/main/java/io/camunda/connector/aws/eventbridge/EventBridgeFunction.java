/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.eventbridge;

import static io.camunda.connector.aws.AwsUtils.extractRegionOrDefault;

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
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

@OutboundConnector(
    name = "AWS EventBridge",
    inputVariables = {"authentication", "configuration", "input"},
    type = "io.camunda:aws-eventbridge:1")
@ElementTemplate(
    engineVersion = "^8.3",
    id = "io.camunda.connectors.AWSEventBridge.v1",
    name = "Amazon EventBridge Outbound Connector",
    description = "Send events to AWS EventBridge",
    metadata =
        @ElementTemplate.Metadata(
            keywords = {"emit event", "publish event", "send event", "trigger event"}),
    inputDataClass = AwsEventBridgeRequest.class,
    version = 6,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Queue properties"),
      @ElementTemplate.PropertyGroup(id = "eventDetails", label = "Event Details"),
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
    EventBridgeClient amazonEventBridgeClient = createEventBridgeClient(eventBridgeRequest);
    return objectMapper.convertValue(
        putEvents(amazonEventBridgeClient, eventBridgeRequest.getInput()), Object.class);
  }

  private EventBridgeClient createEventBridgeClient(final AwsEventBridgeRequest request) {
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

  public PutEventsResponse putEvents(final EventBridgeClient client, final AwsEventBridgeInput input)
      throws JsonProcessingException {
    PutEventsRequestEntry entry =
        PutEventsRequestEntry.builder()
            .source(input.getSource())
            .detailType(input.getDetailType())
            .eventBusName(input.getEventBusName())
            .detail(objectMapper.writeValueAsString(input.getDetail()))
        .build();
    return client.putEvents(PutEventsRequest.builder().entries(entry)
        .build());
  }
}
