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
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * Result of a sandbox CREATE operation, returned by the sandbox BPMN element when a new sandbox is
 * provisioned.
 */
@AgenticAiRecord
@JsonDeserialize(builder = SandboxCreateResult.SandboxCreateResultJacksonProxyBuilder.class)
public record SandboxCreateResult(
    String handle,
    String workDir,
    @Nullable @JsonInclude(JsonInclude.Include.NON_EMPTY) List<SkillCatalogEntry> catalog)
    implements SandboxCreateResultBuilder.With {

  public static SandboxCreateResultBuilder builder() {
    return SandboxCreateResultBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class SandboxCreateResultJacksonProxyBuilder extends SandboxCreateResultBuilder {}
}
