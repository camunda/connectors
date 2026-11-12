/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message.content;

/**
 * A content block that a chat model provider can receive: {@link PortableContent}, which any
 * provider can receive, or a block that carries the provider that made it ({@link
 * ReasoningContent}, {@link ProviderContent}). Only an assistant message of a chat request holds
 * the blocks that carry a provider. {@link AgentDocumentContent} is not a chat content block.
 */
public sealed interface ChatContent extends Content
    permits PortableContent, ReasoningContent, ProviderContent {}
