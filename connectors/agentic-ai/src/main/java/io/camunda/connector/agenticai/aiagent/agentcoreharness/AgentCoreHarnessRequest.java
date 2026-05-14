/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentcoreharness;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerAgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.JobWorkerAgentRequest.JobWorkerAgentRequestData;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.AwsAuthentication;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Request model for the AgentCore Harness job worker.
 *
 * @param toolElements the ad-hoc subprocess inner elements (become inline_function tools)
 * @param agentContext the agent context from previous iterations
 * @param toolCallResults results from tool executions
 * @param harness harness configuration (ARN, region, allowed tools)
 * @param authentication AWS authentication configuration
 * @param userPrompt the user prompt for the first turn
 * @param maxIterations maximum iterations before forcing completion
 */
public record AgentCoreHarnessRequest(
    @JsonProperty("adHocSubProcessElements") List<AdHocToolElement> toolElements,
    @Valid AgentContext agentContext,
    List<ToolCallResult> toolCallResults,
    @Valid @NotNull HarnessConfiguration harness,
    @Valid @NotNull AwsAuthentication authentication,
    @NotBlank String userPrompt,
    Integer maxIterations) {

  /**
   * Harness-specific configuration.
   *
   * @param harnessArn full ARN of the deployed AgentCore Harness
   * @param region AWS region (optional, inferred from ARN if omitted)
   * @param allowedTools optional list of tool names to scope the harness to
   */
  public record HarnessConfiguration(
      @NotBlank String harnessArn, String region, List<String> allowedTools) {

    /** Extract region from ARN if not explicitly provided. */
    public String effectiveRegion() {
      if (region != null && !region.isBlank()) {
        return region;
      }
      // ARN format: arn:aws:bedrock-agentcore:REGION:ACCOUNT:harness/NAME
      if (harnessArn != null && harnessArn.startsWith("arn:aws:")) {
        String[] parts = harnessArn.split(":");
        if (parts.length >= 4) {
          return parts[3];
        }
      }
      return "us-east-1"; // default
    }
  }

  /**
   * Convert to the standard JobWorkerAgentRequest format for compatibility with existing handlers.
   */
  public JobWorkerAgentRequest toAgentRequest() {
    return new JobWorkerAgentRequest(
        toolElements,
        agentContext,
        toolCallResults,
        null, // provider is not used for Harness
        new JobWorkerAgentRequestData(
            new SystemPromptConfiguration(null), // system prompt comes from Harness config
            new UserPromptConfiguration(userPrompt, null),
            null, // memory
            null, // limits
            null, // events
            null // response
            ));
  }
}
