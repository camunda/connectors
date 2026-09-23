/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions;

import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiRequestCustomizations;
import org.jspecify.annotations.Nullable;

/**
 * Provider-neutral input to {@link OpenAiCompletionsRequestConverter}: everything the OpenAI Chat
 * Completions wire mapper needs to build a request, decoupled from any specific provider's
 * configuration type. {@code maxCompletionTokens} and {@code maxTokens} are kept as two distinct
 * fields rather than unified, because they are two different wire parameters -- OpenAI's current
 * name and the older, still-widely-implemented {@code max_tokens} name several OpenAI-compatible
 * APIs (e.g. Mistral) require instead -- and at most one is ever set by a given caller.
 *
 * <p>Callers build this from their own configuration type (see {@code OpenAiCompletionsStrategy}
 * for the OpenAI provider's mapping); the converter itself has no dependency on any {@code
 * ProviderConfiguration} subtype.
 */
public record CompletionsRequestSpec(
    String model,
    @Nullable Long maxCompletionTokens,
    @Nullable Long maxTokens,
    @Nullable Double temperature,
    @Nullable Double topP,
    @Nullable String reasoningEffort,
    OpenAiRequestCustomizations customizations) {}
