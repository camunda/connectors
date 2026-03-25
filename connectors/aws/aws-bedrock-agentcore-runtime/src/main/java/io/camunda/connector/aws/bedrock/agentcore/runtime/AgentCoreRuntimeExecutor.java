/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.agentcore.runtime;

import io.camunda.connector.aws.bedrock.agentcore.runtime.model.request.AgentCoreRuntimeRequest;
import io.camunda.connector.aws.bedrock.agentcore.runtime.model.response.AgentCoreRuntimeResponse;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeAgentRuntimeRequest;

public class AgentCoreRuntimeExecutor {

  private final BedrockAgentCoreClient client;

  public AgentCoreRuntimeExecutor(BedrockAgentCoreClient client) {
    this.client = client;
  }

  public AgentCoreRuntimeResponse invoke(AgentCoreRuntimeRequest request) {
    var builder =
        InvokeAgentRuntimeRequest.builder()
            .agentRuntimeArn(request.getAgentRuntimeArn())
            .payload(
                SdkBytes.fromUtf8String(
                    "{\"inputText\":\"" + escapeJson(request.getPrompt()) + "\"}"))
            .contentType("application/json")
            .accept("application/json");

    if (request.getSessionId() != null && !request.getSessionId().isBlank()) {
      builder.runtimeSessionId(request.getSessionId());
    }

    var responseBytes = client.invokeAgentRuntimeAsBytes(builder.build());
    var responseText = responseBytes.asUtf8String();
    var sessionId = responseBytes.response().runtimeSessionId();
    var statusCode = responseBytes.response().statusCode();

    return new AgentCoreRuntimeResponse(responseText, sessionId, statusCode);
  }

  private static String escapeJson(String text) {
    return text.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
