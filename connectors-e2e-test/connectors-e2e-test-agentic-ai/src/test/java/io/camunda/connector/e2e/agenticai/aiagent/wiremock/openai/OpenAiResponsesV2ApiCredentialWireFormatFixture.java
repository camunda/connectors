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
 * Drives the native v2 {@code openai-api} backend through a saved {@code OpenAiApiCredential}
 * rather than an inline {@code apiKey}/{@code organizationId}/{@code projectId} — the wire format
 * itself is identical to {@link OpenAiResponsesV2WireFormatFixture}'s, so this only proves the
 * credential path reaches the same wire call.
 */
public final class OpenAiResponsesV2ApiCredentialWireFormatFixture
    implements ProviderWireFormatFixture {

  @Override
  public String apiName() {
    return "OpenAiResponsesV2ApiCredential";
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
            .property("provider.openai.api.type", "responses")
            .property("provider.openai.backend.type", "openai-api")
            .property("provider.openai.backend.openai.endpoint", wireMock.getHttpBaseUrl() + "/v1")
            .property(
                "provider.openai.backend.openai.openAiApiCredential",
                "={\"apiKey\": \"dummy\", \"organizationId\": \"org-dummy\", \"projectId\":"
                    + " \"proj-dummy\"}")
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
    OpenAiResponsesV2SseChatModelStubs.stubConversation(turns);
  }

  @Override
  public List<RecordedChatRequest> recordedRequests() {
    return OpenAiResponsesV2RecordedConversation.recorded().requests().stream()
        .<RecordedChatRequest>map(OpenAiResponsesV2RecordedChatRequestAdapter::new)
        .toList();
  }
}
