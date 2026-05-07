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
 * Per-call tunables passed alongside a {@link ChatRequest}. {@code reasoning} and {@code
 * cacheRetention} are normalised across providers; {@code providerOptions} is the typed escape
 * hatch for vendor-specific knobs (Anthropic beta flags, Bedrock guardrail config, etc.).
 *
 * <p>Part of the ADR-004 Phase 1 SPI scaffolding. Not yet wired into the runtime.
 */
public record ChatOptions(
    @Nullable Integer maxOutputTokens,
    @Nullable Double temperature,
    @Nullable ReasoningConfig reasoning,
    @Nullable CacheRetention cacheRetention,
    Map<String, Object> providerOptions) {}
