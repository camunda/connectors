/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.LlmProviderConfiguration;

/**
 * {@link ChatModelApiConfiguration} backed by the wire-format-first {@link
 * LlmProviderConfiguration} surfaced by the v2 connectors. LLM-provider factories (Anthropic,
 * OpenAI) {@code supports(...)} this type for the backends they serve; the registry fails loud with
 * {@code ERROR_CODE_FAILED_MODEL_CALL} for any configuration no factory supports. The per-element
 * capability override is reached via {@link LlmProviderConfiguration#capabilityOverride()} — do not
 * add a second component here.
 */
public record LlmProviderChatModelApiConfiguration(LlmProviderConfiguration configuration)
    implements ChatModelApiConfiguration {}
