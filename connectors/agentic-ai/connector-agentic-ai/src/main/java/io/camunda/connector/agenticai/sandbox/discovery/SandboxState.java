/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.discovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.common.AgenticAiRecord;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * Durable sandbox-level state stored once in {@code agentContext.properties} under key {@link
 * io.camunda.connector.agenticai.sandbox.discovery.SandboxGatewayToolHandler#PROPERTY_SANDBOX}.
 * Holds the BPMN element id, the opaque sandbox handle, the working directory, and the optional
 * skill catalog — all previously duplicated across five per-tool metadata maps.
 */
@AgenticAiRecord
@JsonDeserialize(builder = SandboxState.SandboxStateJacksonProxyBuilder.class)
public record SandboxState(
    String elementId,
    @Nullable String handle,
    @Nullable String workDir,
    // TODO(skills-bloat): the catalog is read exactly once, at the first (frozen) system-prompt
    // composition (see SandboxSkillsSystemPromptContributor). Since the system prompt is now frozen
    // after turn 1, the catalog is dead weight on every subsequent turn. Strip it from the
    // persisted
    // SandboxState once the prompt has been frozen.
    @Nullable @JsonInclude(JsonInclude.Include.NON_EMPTY) List<SkillCatalogEntry> catalog)
    implements SandboxStateBuilder.With {

  public static SandboxStateBuilder builder() {
    return SandboxStateBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class SandboxStateJacksonProxyBuilder extends SandboxStateBuilder {}
}
