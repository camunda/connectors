/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel;

import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.V2ProviderConfiguration;

/**
 * {@link ChatModelApiConfiguration} backed by the wire-format-first {@link V2ProviderConfiguration}
 * surfaced by the v2 connectors. LLM-provider factories (Anthropic, OpenAI) {@code supports(...)}
 * this type for the backends they serve; the registry fails loud with {@code
 * ERROR_CODE_FAILED_MODEL_CALL} for any configuration no factory supports. The per-element
 * capability override is reached via {@link V2ProviderConfiguration#capabilityOverride()} — do not
 * add a second component here.
 */
public record V2ChatModelApiConfiguration(V2ProviderConfiguration configuration)
    implements ChatModelApiConfiguration {}
