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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ProviderWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.Map;
import java.util.function.Function;

/**
 * Plugs Anthropic's Messages API wire format into the provider-agnostic {@link
 * ProviderWireFormatFixture} SPI, driving the connector through its native (own-LLM-layer,
 * non-langchain4j) Anthropic provider (v2) — see {@code AnthropicOkHttpClientFactory}. The
 * *request* wire format is identical to the langchain4j-bridge provider; see {@link
 * AbstractAnthropicMessagesWireFormatFixture} for the shared plumbing ({@code recordedRequests()},
 * {@code assertResponseFormatConfigured(...)}).
 *
 * <p>The *response* wire format differs and is therefore NOT shared: {@code AnthropicChatModelApi}
 * always calls the vendor SDK's streaming endpoint ({@code
 * client.messages().createStreaming(params)}), which expects a real {@code text/event-stream} SSE
 * body, whereas the langchain4j-bridge client (v1) issues a plain non-streaming POST and expects a
 * single buffered JSON body. {@link #stubConversation(TurnStub...)} is overridden here to stub the
 * former via {@link StreamingAnthropicMessagesSseChatModelStubs} instead of inheriting {@link
 * AbstractAnthropicMessagesWireFormatFixture}'s buffered-JSON default.
 *
 * <p>Drives the v2 element template ({@link
 * AiAgentTestFixtures#AI_AGENT_JOB_WORKER_V2_ELEMENT_TEMPLATE_PATH}), whose provider/backend
 * property ids share the v1 template's {@code provider.*} prefix but nest a differently-shaped
 * schema underneath it — see {@link #elementTemplatePath(String)} and {@link
 * #elementTemplateBaselineProperties(Map)}.
 *
 * <p>The configured endpoint is the bare WireMock host root (no trailing {@code /v1/}), unlike the
 * v1 fixture: the native Anthropic SDK ({@code com.anthropic:anthropic-java}) always appends both
 * {@code v1} and {@code messages} path segments onto the configured base URL itself, whereas the
 * langchain4j Anthropic client expects the endpoint to already include {@code /v1}.
 */
public final class StreamingAnthropicMessagesWireFormatFixture
    extends AbstractAnthropicMessagesWireFormatFixture {

  @Override
  public String apiName() {
    return "StreamingAnthropicMessages";
  }

  @Override
  public void stubConversation(TurnStub... turns) {
    StreamingAnthropicMessagesSseChatModelStubs.stubConversation(turns);
  }

  @Override
  public Function<ElementTemplate, ElementTemplate> configureProvider(
      WireMockRuntimeInfo wireMock) {
    return template ->
        template
            .property("provider.type", "anthropic")
            .property("provider.anthropic.backend.type", "direct")
            .property("provider.anthropic.backend.direct.endpoint", wireMock.getHttpBaseUrl())
            .property("provider.anthropic.backend.apiKey", "dummy")
            .property("provider.anthropic.model.model", "test-model");
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
}
