/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.agentcore.runtime;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.aws.bedrock.agentcore.runtime.model.request.AgentCoreRuntimeInput;
import io.camunda.connector.aws.bedrock.agentcore.runtime.model.response.AgentCoreRuntimeResponse;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.BedrockAgentCoreException;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeAgentRuntimeRequest;

public class AgentCoreRuntimeExecutor {

  private static final String ERROR_RUNTIME_FAILED = "AGENTCORE_RUNTIME_FAILED";

  private final BedrockAgentCoreClient client;

  public AgentCoreRuntimeExecutor(BedrockAgentCoreClient client) {
    this.client = client;
  }

  public AgentCoreRuntimeResponse invoke(AgentCoreRuntimeInput input) {
    try {
      var builder =
          InvokeAgentRuntimeRequest.builder()
              .agentRuntimeArn(input.getAgentRuntimeArn())
              .payload(
                  SdkBytes.fromUtf8String(
                      "{\"inputText\":\"" + escapeJson(input.getPrompt()) + "\"}"))
              .contentType("application/json")
              .accept("application/json");

      if (input.getSessionId() != null && !input.getSessionId().isBlank()) {
        builder.runtimeSessionId(input.getSessionId());
      }

      var responseBytes = client.invokeAgentRuntimeAsBytes(builder.build());
      return new AgentCoreRuntimeResponse(
          responseBytes.asUtf8String(),
          responseBytes.response().runtimeSessionId(),
          responseBytes.response().statusCode());

    } catch (BedrockAgentCoreException e) {
      var msg = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
      throw new ConnectorException(ERROR_RUNTIME_FAILED, "AgentCore Runtime error: " + msg, e);
    }
  }

  private static String escapeJson(String text) {
    return text.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
