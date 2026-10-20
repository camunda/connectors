/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.daytona;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.connector.agenticai.sandbox.discovery.SandboxOperation;
import io.camunda.connector.api.document.Document;
import org.jspecify.annotations.Nullable;

/** Internal DTO representing a tool call dispatched by the AI agent to the Daytona sandbox. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SandboxToolCall(
    SandboxOperation operation,
    @JsonProperty("handle") @Nullable String sandboxId,
    @Nullable String command,
    @Nullable String path,
    @Nullable String content,
    @Nullable Document document,
    @Nullable Long agentInstanceKey) {}
