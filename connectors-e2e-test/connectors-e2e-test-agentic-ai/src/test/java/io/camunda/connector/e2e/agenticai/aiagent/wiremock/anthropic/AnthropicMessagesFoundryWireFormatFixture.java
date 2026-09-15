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
import io.camunda.connector.e2e.agenticai.aiagent.AgentTestFixtures;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ProviderWireFormatFixture;
import java.util.Map;
import java.util.function.Function;

/**
 * Plugs Anthropic's Messages API wire format into the provider-agnostic {@link
 * ProviderWireFormatFixture} SPI, driving the connector through the {@code foundry} backend of the
 * native v2 Anthropic provider — see {@code AnthropicFoundryBackend}. The SDK's {@code
 * FoundryBackend} auto-appends {@code /anthropic} onto the configured base URL, so the recorded
 * requests land on {@code /anthropic/v1/messages} rather than the bare {@code /v1/messages} the
 * other Anthropic fixtures use.
 */
public final class AnthropicMessagesFoundryWireFormatFixture
    extends AbstractAnthropicMessagesWireFormatFixture {

  @Override
  public String apiName() {
    return "AnthropicMessagesFoundry";
  }

  @Override
  public Function<ElementTemplate, ElementTemplate> configureProvider(
      WireMockRuntimeInfo wireMock) {
    return template ->
        template
            .property("provider.type", "anthropic")
            .property("provider.anthropic.backend.type", "foundry")
            .property("provider.anthropic.backend.foundry.endpoint", wireMock.getHttpBaseUrl())
            .property("provider.anthropic.backend.foundry.authentication.type", "apiKey")
            .property("provider.anthropic.backend.foundry.authentication.apiKey", "dummy")
            .property("provider.anthropic.model.model", "test-model");
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
}
