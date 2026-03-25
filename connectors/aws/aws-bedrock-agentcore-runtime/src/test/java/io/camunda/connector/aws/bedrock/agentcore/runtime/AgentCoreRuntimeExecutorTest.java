/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.agentcore.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.camunda.connector.aws.bedrock.agentcore.runtime.model.request.AgentCoreRuntimeRequest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeAgentRuntimeRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeAgentRuntimeResponse;

class AgentCoreRuntimeExecutorTest extends BaseTest {

  private BedrockAgentCoreClient client;
  private AgentCoreRuntimeExecutor executor;

  @BeforeEach
  void setUp() {
    client = mock(BedrockAgentCoreClient.class);
    executor = new AgentCoreRuntimeExecutor(client);
  }

  private AgentCoreRuntimeRequest createRequest(String prompt, String sessionId) {
    var request = new AgentCoreRuntimeRequest();
    request.setAgentRuntimeArn(AGENT_RUNTIME_ARN);
    request.setPrompt(prompt);
    request.setSessionId(sessionId);
    return request;
  }

  private void mockResponse(String body, String sessionId, int statusCode) {
    var response =
        InvokeAgentRuntimeResponse.builder()
            .runtimeSessionId(sessionId)
            .statusCode(statusCode)
            .build();
    var responseBytes =
        ResponseBytes.fromByteArray(response, body.getBytes(StandardCharsets.UTF_8));
    when(client.invokeAgentRuntimeAsBytes(any(InvokeAgentRuntimeRequest.class)))
        .thenReturn(responseBytes);
  }

  @Test
  void shouldInvokeAgentAndReturnResponse() {
    mockResponse("Fraud risk is LOW for claim CLM-001.", SESSION_ID, 200);
    var result = executor.invoke(createRequest(PROMPT, null));

    assertThat(result.response()).isEqualTo("Fraud risk is LOW for claim CLM-001.");
    assertThat(result.sessionId()).isEqualTo(SESSION_ID);
    assertThat(result.statusCode()).isEqualTo(200);
  }

  @Test
  void shouldPassAgentRuntimeArnAndPrompt() {
    mockResponse("ok", SESSION_ID, 200);
    var captor = ArgumentCaptor.forClass(InvokeAgentRuntimeRequest.class);

    executor.invoke(createRequest(PROMPT, null));

    verify(client).invokeAgentRuntimeAsBytes(captor.capture());
    assertThat(captor.getValue().agentRuntimeArn()).isEqualTo(AGENT_RUNTIME_ARN);
    assertThat(captor.getValue().payload().asUtf8String()).contains("What is the fraud risk");
  }

  @Test
  void shouldPassSessionIdWhenProvided() {
    mockResponse("ok", SESSION_ID, 200);
    var captor = ArgumentCaptor.forClass(InvokeAgentRuntimeRequest.class);

    executor.invoke(createRequest(PROMPT, SESSION_ID));

    verify(client).invokeAgentRuntimeAsBytes(captor.capture());
    assertThat(captor.getValue().runtimeSessionId()).isEqualTo(SESSION_ID);
  }

  @Test
  void shouldNotSetSessionIdWhenNull() {
    mockResponse("ok", SESSION_ID, 200);
    var captor = ArgumentCaptor.forClass(InvokeAgentRuntimeRequest.class);

    executor.invoke(createRequest(PROMPT, null));

    verify(client).invokeAgentRuntimeAsBytes(captor.capture());
    assertThat(captor.getValue().runtimeSessionId()).isNull();
  }
}
