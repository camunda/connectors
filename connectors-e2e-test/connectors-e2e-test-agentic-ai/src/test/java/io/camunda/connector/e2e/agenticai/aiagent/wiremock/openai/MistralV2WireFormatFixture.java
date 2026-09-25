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
 * Plugs the native Mistral provider into the provider-agnostic {@link ProviderWireFormatFixture}
 * SPI. Mistral's Chat Completions API is the same wire format as OpenAI's (see {@code
 * MistralChatModel}, which reuses the OpenAI provider's Completions request/response converters
 * wholesale), so -- exactly like {@link AzureOpenAiCompletionsWireFormatFixture} -- this fixture
 * reuses {@link OpenAiCompletionsChatModelStubs}, {@link OpenAiCompletionsRecordedConversation} and
 * {@link OpenAiCompletionsRecordedChatRequestAdapter} directly rather than duplicating them, at the
 * same default {@code /v1/chat/completions} path.
 *
 * <p>Chunked (Magistral-style) reasoning content is exercised separately in {@code
 * AgentSubProcessMistralReasoningTests}, not by this fixture's shared cross-provider scenarios.
 */
public final class MistralV2WireFormatFixture implements ProviderWireFormatFixture {

  @Override
  public String apiName() {
    return "MistralV2";
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
            .property("provider.type", "mistral")
            .property("provider.mistral.backend.type", "mistral-api")
            .property(
                "provider.mistral.backend.mistral.endpoint", wireMock.getHttpBaseUrl() + "/v1")
            .property("provider.mistral.backend.mistral.apiKey", "dummy")
            .property("provider.mistral.model.model", "test-model");
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
    OpenAiCompletionsChatModelStubs.stubConversation(turns);
  }

  @Override
  public List<RecordedChatRequest> recordedRequests() {
    return OpenAiCompletionsRecordedConversation.recorded().requests().stream()
        .<RecordedChatRequest>map(OpenAiCompletionsRecordedChatRequestAdapter::new)
        .toList();
  }
}
