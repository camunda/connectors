/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Sparse, operator-supplied capability override — the highest-precedence layer fed to {@link
 * ModelCapabilitiesResolver#resolve}. Every field is {@link Nullable}: a {@code null} field is
 * absent and inherits from the resolved base (bundled YAML + operator {@code application.yml} +
 * matrix), a non-null field wins.
 *
 * <p>Field names mirror {@link ModelCapabilities} (the friendly, full names). {@link
 * #toSparseJsonNode} projects them onto the internal {@link ModelCapabilitiesData} snake/nested
 * shape used by the deep-merge, omitting any null field so only explicitly-set values overlay:
 *
 * <ul>
 *   <li>{@code userMessageModalities} -&gt; {@code input_modalities.user_message}
 *   <li>{@code toolResultModalities} -&gt; {@code input_modalities.tool_result}
 *   <li>{@code assistantMessageModalities} -&gt; {@code output_modalities.assistant_message}
 *   <li>{@code supportsReasoning} -&gt; {@code supports_reasoning} (and the other flags/counters by
 *       the same snake-case rule)
 * </ul>
 *
 * <p>Kept in the capabilities package (not the config package) so the dependency direction stays
 * config -&gt; framework. The connector config surfaces it as a {@code @FEEL} field; the FEEL
 * expression evaluates to a sparse map/context whose keys are these component names.
 */
public record ModelCapabilitiesOverride(
    @Nullable List<Modality> userMessageModalities,
    @Nullable List<Modality> toolResultModalities,
    @Nullable List<Modality> assistantMessageModalities,
    @Nullable Boolean supportsReasoning,
    @Nullable Boolean supportsReasoningSignatureRoundtrip,
    @Nullable Boolean supportsPromptCaching,
    @Nullable Boolean supportsParallelToolCalls,
    @Nullable Integer contextWindow,
    @Nullable Integer maxOutputTokens) {

  /**
   * Projects this sparse override onto a {@link ModelCapabilitiesData}-shaped {@link JsonNode},
   * omitting every null field (and omitting an empty {@code input_modalities} / {@code
   * output_modalities} branch), so it can be deep-merged as the top overlay.
   */
  public JsonNode toSparseJsonNode(ObjectMapper mapper) {
    final ObjectNode root = mapper.createObjectNode();

    final ObjectNode input = mapper.createObjectNode();
    if (userMessageModalities != null) {
      input.set("user_message", modalitiesArray(mapper, userMessageModalities));
    }
    if (toolResultModalities != null) {
      input.set("tool_result", modalitiesArray(mapper, toolResultModalities));
    }
    if (!input.isEmpty()) {
      root.set("input_modalities", input);
    }

    final ObjectNode output = mapper.createObjectNode();
    if (assistantMessageModalities != null) {
      output.set("assistant_message", modalitiesArray(mapper, assistantMessageModalities));
    }
    if (!output.isEmpty()) {
      root.set("output_modalities", output);
    }

    if (supportsReasoning != null) {
      root.put("supports_reasoning", supportsReasoning);
    }
    if (supportsReasoningSignatureRoundtrip != null) {
      root.put("supports_reasoning_signature_roundtrip", supportsReasoningSignatureRoundtrip);
    }
    if (supportsPromptCaching != null) {
      root.put("supports_prompt_caching", supportsPromptCaching);
    }
    if (supportsParallelToolCalls != null) {
      root.put("supports_parallel_tool_calls", supportsParallelToolCalls);
    }
    if (contextWindow != null) {
      root.put("context_window", contextWindow);
    }
    if (maxOutputTokens != null) {
      root.put("max_output_tokens", maxOutputTokens);
    }

    return root;
  }

  private static ArrayNode modalitiesArray(ObjectMapper mapper, List<Modality> modalities) {
    final ArrayNode array = mapper.createArrayNode();
    for (Modality modality : modalities) {
      // Modality carries lowercase @JsonProperty values ("text"/"image"/...) matching the YAML.
      array.add(mapper.convertValue(modality, String.class));
    }
    return array;
  }
}
