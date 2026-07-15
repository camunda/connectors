/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiEffort;
import java.util.List;

/**
 * Typed OpenAI reasoning descriptor, materialised from a model's {@code provider.reasoning} matrix
 * block. {@code effort-levels} lists supported effort values (empty ⇒ effort unsupported). Unlike
 * the Anthropic sibling, OpenAI reasoning has no {@code thinking-modes} axis — effort is the only
 * reasoning control.
 */
public record OpenAiReasoningCapabilities(
    @JsonProperty("effort-levels") List<OpenAiEffort> effortLevels) {

  public OpenAiReasoningCapabilities {
    effortLevels = effortLevels == null ? List.of() : List.copyOf(effortLevels);
  }
}
