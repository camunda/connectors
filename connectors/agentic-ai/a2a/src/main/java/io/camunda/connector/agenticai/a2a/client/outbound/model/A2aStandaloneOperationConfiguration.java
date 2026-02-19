/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.outbound.model;

import static io.camunda.connector.agenticai.a2a.client.outbound.model.A2aStandaloneOperationConfiguration.FetchAgentCardOperationConfiguration.FETCH_AGENT_CARD_ID;
import static io.camunda.connector.agenticai.a2a.client.outbound.model.A2aStandaloneOperationConfiguration.SendMessageOperationConfiguration.SEND_MESSAGE_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = A2aStandaloneOperationConfiguration.FetchAgentCardOperationConfiguration.class,
      name = FETCH_AGENT_CARD_ID),
  @JsonSubTypes.Type(
      value = A2aStandaloneOperationConfiguration.SendMessageOperationConfiguration.class,
      name = SEND_MESSAGE_ID)
})
@TemplateDiscriminatorProperty(
    group = "operation",
    label = "Operation",
    name = "type",
    description = "The type of operation to perform.",
    defaultValue = FETCH_AGENT_CARD_ID)
public sealed interface A2aStandaloneOperationConfiguration
    permits A2aStandaloneOperationConfiguration.FetchAgentCardOperationConfiguration,
        A2aStandaloneOperationConfiguration.SendMessageOperationConfiguration {

  @TemplateSubType(id = FETCH_AGENT_CARD_ID, label = "Fetch Agent Card")
  record FetchAgentCardOperationConfiguration() implements A2aStandaloneOperationConfiguration {

    @TemplateProperty(ignore = true)
    public static final String FETCH_AGENT_CARD_ID = "fetchAgentCard";
  }

  @TemplateSubType(id = SEND_MESSAGE_ID, label = "Send message")
  record SendMessageOperationConfiguration(
      @Valid @NotNull A2aSendMessageOperationParameters params,
      @Valid @NotNull A2aCommonSendMessageConfiguration settings)
      implements A2aStandaloneOperationConfiguration {

    @TemplateProperty(ignore = true)
    public static final String SEND_MESSAGE_ID = "sendMessage";
  }
}
