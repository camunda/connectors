/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.AgentTestFixtures;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ProviderWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedChatRequest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Plugs the native (own-LLM-layer) OpenAI Chat Completions wire format into the provider-agnostic
 * {@link ProviderWireFormatFixture} SPI, driving the connector through the v2 native OpenAI
 * provider - see {@code OpenAiChatModelFactory}. The *request* wire is the standard Chat
 * Completions body - identical to the legacy langchain4j-bridge fixture's - so request recording is
 * reused via {@link OpenAiCompletionsRecordedConversation} / {@link
 * OpenAiCompletionsRecordedChatRequestAdapter} rather than duplicating the parsing logic (mirrors
 * how the reference pilot branch's streaming Completions fixture reused the same non-streaming
 * request parser). The *response* wire differs (native streams SSE, the legacy bridge buffers
 * JSON), so {@link #stubConversation} uses {@link NativeOpenAiCompletionsSseChatModelStubs}.
 *
 * <p>Drives the v2 element template with {@code provider.openai.*} property ids, via the {@code
 * custom} backend (the only OpenAI backend with a configurable endpoint) pointed at the WireMock
 * host with a trailing {@code /v1} so the SDK's {@code /chat/completions} path resolves to the
 * recorded path - the native SDK client's {@code custom} backend does not re-append {@code /v1}
 * itself, it appends only the relative operation path onto whatever base URL is configured (see
 * {@code OpenAiChatModelFactory} and the {@code CustomBackend.endpoint} template property
 * description: "Base URL of the OpenAI-compatible API; /chat/completions or /responses will be
 * appended").
 */
public final class NativeOpenAiCompletionsWireFormatFixture implements ProviderWireFormatFixture {

  @Override
  public String apiName() {
    return "NativeOpenAiCompletions";
  }

  @Override
  public String toString() {
    return apiName();
  }

  @Override
  public Function<ElementTemplate, ElementTemplate> configureProvider(
      WireMockRuntimeInfo wireMock) {
    return template ->
        template
            .property("provider.type", "openai")
            .property("provider.openai.api.type", "completions")
            .property("provider.openai.backend.type", "custom")
            .property("provider.openai.backend.custom.endpoint", wireMock.getHttpBaseUrl() + "/v1")
            .property("provider.openai.backend.custom.authentication.type", "apiKey")
            .property("provider.openai.backend.custom.authentication.apiKey", "dummy")
            .property("provider.openai.model.model", "test-model");
  }

  @Override
  public String elementTemplatePath(String defaultElementTemplatePath) {
    return AgentTestFixtures.AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH;
  }

  @Override
  public Map<String, String> elementTemplateBaselineProperties(
      Map<String, String> defaultProperties) {
    return AgentTestFixtures.AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PROPERTIES;
  }

  @Override
  public void stubConversation(TurnStub... turns) {
    NativeOpenAiCompletionsSseChatModelStubs.stubConversation(turns);
  }

  @Override
  public List<RecordedChatRequest> recordedRequests() {
    return OpenAiCompletionsRecordedConversation.recorded().requests().stream()
        .<RecordedChatRequest>map(OpenAiCompletionsRecordedChatRequestAdapter::new)
        .toList();
  }
}
