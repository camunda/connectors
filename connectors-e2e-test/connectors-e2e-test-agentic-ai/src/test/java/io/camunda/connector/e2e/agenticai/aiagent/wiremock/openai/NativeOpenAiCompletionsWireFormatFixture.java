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
import io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ProviderWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedChatRequest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Plugs the native (own-LLM-layer) OpenAI Chat Completions wire format into the provider-agnostic
 * {@link ProviderWireFormatFixture} SPI. The *request* wire is the standard Chat Completions body -
 * identical to the v1 langchain4j-bridge fixture - so request recording is reused via {@link
 * OpenAiCompletionsRecordedConversation}. The *response* wire differs (native streams SSE, v1
 * buffers JSON), so {@link #stubConversation} uses {@link
 * NativeOpenAiCompletionsSseChatModelStubs}.
 *
 * <p>Drives the v2 element template with {@code provider.openai.*} property ids, via the compatible
 * backend (the only OpenAI backend with a configurable endpoint) pointed at the WireMock host with
 * a trailing {@code /v1} so the SDK's {@code /chat/completions} path resolves to the recorded path
 * (the native SDK client does not re-append {@code /v1} itself, unlike the langchain4j-bridge
 * client, which expects the endpoint to already include it - see {@code
 * OpenAiOkHttpClientFactory#applyCompatibleBackend}).
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
            .property("provider.openai.apiFamily", "completions")
            .property("provider.openai.backend.type", "compatible")
            .property("provider.openai.backend.endpoint", wireMock.getHttpBaseUrl() + "/v1")
            .property("provider.openai.backend.authentication.type", "apiKey")
            .property("provider.openai.backend.authentication.apiKey", "dummy")
            .property("provider.openai.model.model", "test-model");
  }

  @Override
  public String elementTemplatePath(String defaultElementTemplatePath) {
    return AiAgentTestFixtures.AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH;
  }

  @Override
  public Map<String, String> elementTemplateBaselineProperties(
      Map<String, String> defaultProperties) {
    return AiAgentTestFixtures.AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PROPERTIES;
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
