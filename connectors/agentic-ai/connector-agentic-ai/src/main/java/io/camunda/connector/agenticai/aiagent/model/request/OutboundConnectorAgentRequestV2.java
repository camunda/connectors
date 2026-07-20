/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import io.camunda.connector.agenticai.aiagent.model.request.OutboundConnectorAgentRequest.OutboundConnectorAgentRequestData;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.V2ProviderConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** v2 AI Agent Task request: wire-format-first chat-model config + the shared v1 request data. */
public record OutboundConnectorAgentRequestV2(
    @Valid @NotNull V2ProviderConfiguration provider,
    @Valid @NotNull OutboundConnectorAgentRequestData data) {}
