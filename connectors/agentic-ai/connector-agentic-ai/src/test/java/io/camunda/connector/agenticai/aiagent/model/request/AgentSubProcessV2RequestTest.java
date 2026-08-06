/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.connector.runtime.core.intrinsic.DefaultIntrinsicFunctionExecutor;
import org.junit.jupiter.api.Test;

class AgentSubProcessV2RequestTest {

  /**
   * Mirrors the production {@code outboundConnectorObjectMapper} bean (see {@code
   * ConnectorsAutoConfiguration#outboundConnectorObjectMapper}), which is the actual ObjectMapper
   * used by {@code JobHandlerContext#bindVariables} for job worker requests: {@code @FEEL}
   * annotation processing is disabled there (FEEL for jobs is evaluated by Zeebe before the
   * variables reach the connector), so field-level {@code @JsonDeserialize} annotations such as
   * {@code VersionedAgentContextDeserializer} take effect instead of being overridden by a
   * FEEL-aware deserializer. This mapper reproduces that by simply never registering the FEEL
   * Jackson module, which has the same effect without pulling in the {@code connector-feel}
   * dependency (undeclared in this module's pom) just to construct a disabled instance of it.
   * {@link io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier} deliberately does NOT
   * mirror this (it enables FEEL annotation processing for other test needs), so it must not be
   * used here.
   */
  private static ObjectMapper outboundObjectMapper() {
    var copy = ConnectorsObjectMapperSupplier.getCopy();
    var documentFactory = new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
    var functionExecutor = new DefaultIntrinsicFunctionExecutor(copy);
    var jacksonModuleDocumentDeserializer =
        new JacksonModuleDocumentDeserializer(
            documentFactory, functionExecutor, DocumentModuleSettings.create());
    return copy.registerModules(
        jacksonModuleDocumentDeserializer, new JacksonModuleDocumentSerializer());
  }

  private final ObjectMapper objectMapper = outboundObjectMapper();

  @Test
  void deserializesLegacyShapedAgentContext() throws Exception {
    // legacy (pre-8.10, pre-CURRENT_SCHEMA_VERSION) persisted agentContext shape: no
    // schemaVersion field and flat string tool-call-result content instead of structured
    // List<Content>. Regression coverage for the missing @JsonDeserialize on
    // AgentSubProcessV2Request.agentContext (mirrors AgentSubProcessV1Request's handling of the
    // same field).
    String json =
        """
        {
          "adHocSubProcessElements": [],
          "agentContext": {
            "state": "READY",
            "metrics": {"modelCalls": 1, "tokenUsage": {"inputTokenCount": 1, "outputTokenCount": 1}},
            "toolDefinitions": [],
            "conversation": {
              "type": "in-process",
              "conversationId": "test",
              "messages": [
                {
                  "role": "tool_call_result",
                  "results": [
                    {"id": "call-1", "name": "search", "content": "Found 3 items"}
                  ]
                }
              ]
            },
            "properties": {}
          },
          "toolCallResults": []
        }
        """;

    AgentSubProcessV2Request request = objectMapper.readValue(json, AgentSubProcessV2Request.class);

    AgentContext agentContext = request.agentContext();
    assertThat(agentContext).isNotNull();
    // migrated on read despite the missing schemaVersion field
    assertThat(agentContext.schemaVersion()).isEqualTo(AgentContext.CURRENT_SCHEMA_VERSION);

    InProcessConversationContext conversation =
        (InProcessConversationContext) agentContext.conversation();
    assertThat(conversation).isNotNull();
    ToolCallResultMessage message = (ToolCallResultMessage) conversation.messages().get(0);

    // flat "content": "Found 3 items" was lifted into a structured List<Content>, not left as a
    // raw string (which would otherwise fail to bind against ToolCallResultContent#content)
    assertThat(message.results().get(0).content())
        .containsExactly(TextContent.textContent("Found 3 items"));
  }
}
