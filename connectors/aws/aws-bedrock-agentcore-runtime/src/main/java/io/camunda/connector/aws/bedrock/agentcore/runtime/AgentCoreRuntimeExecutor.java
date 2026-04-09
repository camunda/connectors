/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.agentcore.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.aws.bedrock.agentcore.runtime.model.request.AgentCoreRuntimeInput;
import io.camunda.connector.aws.bedrock.agentcore.runtime.model.response.AgentCoreRuntimeResponse;
import java.util.Map;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.BedrockAgentCoreException;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeAgentRuntimeRequest;

public class AgentCoreRuntimeExecutor {

  private static final String ERROR_RUNTIME_FAILED = "AGENTCORE_RUNTIME_FAILED";
  private static final String DEFAULT_CONTENT_TYPE = "application/json";

  private final BedrockAgentCoreClient client;
  private final ObjectMapper objectMapper;

  public AgentCoreRuntimeExecutor(BedrockAgentCoreClient client, ObjectMapper objectMapper) {
    this.client = client;
    this.objectMapper = objectMapper;
  }

  public AgentCoreRuntimeResponse invoke(AgentCoreRuntimeInput input) {
    try {
      var contentType =
          input.getContentType() != null ? input.getContentType() : DEFAULT_CONTENT_TYPE;

      var payloadBytes = objectMapper.writeValueAsBytes(input.getPayload());

      var builder =
          InvokeAgentRuntimeRequest.builder()
              .agentRuntimeArn(input.getAgentRuntimeArn())
              .payload(SdkBytes.fromByteArray(payloadBytes))
              .contentType(contentType)
              .accept(contentType);

      if (input.getSessionId() != null && !input.getSessionId().isBlank()) {
        builder.runtimeSessionId(input.getSessionId());
      }

      var responseBytes = client.invokeAgentRuntimeAsBytes(builder.build());
      var responseText = responseBytes.asUtf8String();
      var sessionId = responseBytes.response().runtimeSessionId();
      var statusCode = responseBytes.response().statusCode();

      Object response = tryParseJson(responseText);

      return new AgentCoreRuntimeResponse(response, sessionId, statusCode);

    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          ERROR_RUNTIME_FAILED, "Failed to serialize payload: " + e.getMessage(), e);
    } catch (BedrockAgentCoreException e) {
      var msg = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
      throw new ConnectorException(ERROR_RUNTIME_FAILED, "AgentCore Runtime error: " + msg, e);
    }
  }

  @SuppressWarnings("unchecked")
  private Object tryParseJson(String text) {
    try {
      return objectMapper.readValue(text, Map.class);
    } catch (JsonProcessingException e) {
      // Not valid JSON — return as plain string
      return text;
    }
  }
}
