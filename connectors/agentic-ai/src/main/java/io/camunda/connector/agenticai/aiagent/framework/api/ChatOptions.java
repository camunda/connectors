/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * Per-call tunables passed alongside a {@link ChatRequest}. The shape is intentionally narrow:
 * fields here are the ones with consistent semantics across every wire-protocol family we
 * implement. Sampling parameters that are partially supported (e.g. {@code temperature}, {@code
 * topP}, {@code topK}, {@code seed}, {@code frequencyPenalty}) live in {@link #providerOptions}
 * under their well-known keys; each {@link ChatModelApi} implementation reads the keys it cares
 * about and ignores the rest.
 *
 * <p><strong>Note on {@link #maxOutputTokens} and reasoning tokens:</strong> on most providers
 * (OpenAI Chat Completions, OpenAI Responses, Google GenAI, AWS Bedrock) reasoning / thinking
 * tokens count toward this cap — a small value combined with reasoning enabled can leave zero room
 * for visible output. On Anthropic Claude 4+ thinking tokens do not affect the {@code max_tokens}
 * cap but are still billed in full. Implementations document any provider-specific clamping or
 * adjustment they apply.
 *
 * <p>Resolution of the actual value sent on the wire happens in {@code ChatClient}: explicit
 * caller-supplied value wins, otherwise the resolved {@link ModelCapabilities#maxOutputTokens()} is
 * used as a fallback, otherwise the implementation supplies its own per-API default.
 *
 * <p>Part of the ADR-005 Phase 1 SPI scaffolding. Wired by ChatClientImpl, dispatched via
 * ChatModelApiRegistry.
 */
public record ChatOptions(
    @Nullable Integer maxOutputTokens,
    @Nullable ReasoningConfig reasoning,
    @Nullable CacheRetention cacheRetention,
    Map<String, Object> providerOptions) {}
