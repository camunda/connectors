/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.agentcore.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.aws.bedrock.agentcore.runtime.model.request.AgentCoreRuntimeInput;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.skyscreamer.jsonassert.JSONAssert;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.BedrockAgentCoreException;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeAgentRuntimeRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeAgentRuntimeResponse;

class AgentCoreRuntimeExecutorTest extends BaseTest {

  private static final ObjectMapper OBJECT_MAPPER = ObjectMapperSupplier.getMapperInstance();

  private BedrockAgentCoreClient client;
  private AgentCoreRuntimeExecutor executor;

  @BeforeEach
  void setUp() {
    client = mock(BedrockAgentCoreClient.class);
    executor = new AgentCoreRuntimeExecutor(client, OBJECT_MAPPER);
  }

  private AgentCoreRuntimeInput createInput(Object payload, String sessionId) {
    var input = new AgentCoreRuntimeInput();
    input.setAgentRuntimeArn(AGENT_RUNTIME_ARN);
    input.setPayload(payload);
    input.setSessionId(sessionId);
    return input;
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
  void shouldInvokeAgentAndReturnParsedJsonResponse() {
    mockResponse("{\"result\": \"Fraud risk is LOW\"}", SESSION_ID, 200);
    var result = executor.invoke(createInput(Map.of("inputText", PROMPT), null));

    assertThat(result.response())
        .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
        .containsEntry("result", "Fraud risk is LOW");
    assertThat(result.sessionId()).isEqualTo(SESSION_ID);
    assertThat(result.statusCode()).isEqualTo(200);
  }

  @Test
  void shouldSerializePayloadWithObjectMapper() throws Exception {
    mockResponse("{}", SESSION_ID, 200);
    var captor = ArgumentCaptor.forClass(InvokeAgentRuntimeRequest.class);

    var payload = Map.of("inputText", "Hello \"world\"");
    executor.invoke(createInput(payload, null));

    verify(client).invokeAgentRuntimeAsBytes(captor.capture());
    var payloadJson = captor.getValue().payload().asUtf8String();
    var expected = OBJECT_MAPPER.writeValueAsString(payload);
    JSONAssert.assertEquals(expected, payloadJson, false);
  }

  @Test
  void shouldPassSessionIdWhenProvided() {
    mockResponse("{}", SESSION_ID, 200);
    var captor = ArgumentCaptor.forClass(InvokeAgentRuntimeRequest.class);

    executor.invoke(createInput(Map.of("inputText", PROMPT), SESSION_ID));

    verify(client).invokeAgentRuntimeAsBytes(captor.capture());
    assertThat(captor.getValue().runtimeSessionId()).isEqualTo(SESSION_ID);
  }

  @Test
  void shouldNotSetSessionIdWhenNull() {
    mockResponse("{}", SESSION_ID, 200);
    var captor = ArgumentCaptor.forClass(InvokeAgentRuntimeRequest.class);

    executor.invoke(createInput(Map.of("inputText", PROMPT), null));

    verify(client).invokeAgentRuntimeAsBytes(captor.capture());
    assertThat(captor.getValue().runtimeSessionId()).isNull();
  }

  @Test
  void shouldWrapBedrockExceptionProperly() {
    when(client.invokeAgentRuntimeAsBytes(any(InvokeAgentRuntimeRequest.class)))
        .thenThrow(BedrockAgentCoreException.builder().message("Access denied").build());

    assertThatThrownBy(() -> executor.invoke(createInput(Map.of("inputText", PROMPT), null)))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("AgentCore Runtime error");
  }
}
