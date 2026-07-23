/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.capabilities;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Provider-agnostic capability data shared across provider capability records and usable directly
 * as a neutral {@link ModelCapabilities}. Carries the three modality lists (the neutral contract)
 * plus the provider-agnostic token-budget figures. The token figures are not part of the neutral
 * {@link ModelCapabilities} contract — they are read only by a provider's own converter via the
 * concrete type — so they live here as extra record accessors, not as interface methods.
 *
 * <p>A capability overlay carrying the same data takes this YAML shape in the bundled matrix:
 *
 * <pre>{@code
 * capabilities:
 *   input-modalities:
 *     user-message: [text, image]
 *     tool-result: [text]
 *   output-modalities:
 *     assistant-message: [text]
 *   context-window: 200000
 *   max-output-tokens: 64000
 * }</pre>
 *
 * @param contextWindow the total input+output token budget for a single request (one model call),
 *     not a figure that accumulates across turns
 * @param maxOutputTokens the cap on output tokens for a single model call (one turn)
 */
public record CoreModelCapabilities(
    List<Modality> userMessageModalities,
    List<Modality> toolResultModalities,
    List<Modality> assistantMessageModalities,
    @Nullable Integer contextWindow,
    @Nullable Integer maxOutputTokens)
    implements ModelCapabilities {}
