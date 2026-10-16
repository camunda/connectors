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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.bedrock;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.AgentTestFixtures;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.Map;
import java.util.function.Function;

/**
 * Plugs AWS Bedrock's Converse API wire format into the provider-agnostic {@code
 * ProviderWireFormatFixture} SPI, driving the connector through the v1 element template: the v1
 * template's {@code provider.bedrock.*} config is rewritten onto the native v2 Bedrock provider at
 * the connector boundary, proving a v1 provider config is routed onto the native provider's wire.
 */
public final class BedrockConverseV1WireFormatFixture
    extends AbstractBedrockConverseWireFormatFixture {

  @Override
  public String apiName() {
    return "BedrockConverseV1";
  }

  @Override
  public void stubConversation(TurnStub... turns) {
    StreamingBedrockConverseEventStreamChatModelStubs.stubConversation(turns);
  }

  @Override
  public Function<ElementTemplate, ElementTemplate> configureProvider(
      WireMockRuntimeInfo wireMock) {
    return template ->
        template
            .property("provider.type", "bedrock")
            .property("provider.bedrock.region", "us-east-1")
            .property("provider.bedrock.endpoint", wireMock.getHttpBaseUrl())
            .property("provider.bedrock.authentication.type", "credentials")
            .property("provider.bedrock.authentication.accessKey", "dummy")
            .property("provider.bedrock.authentication.secretKey", "dummy")
            .property("provider.bedrock.model.model", "test-model");
  }

  @Override
  public String elementTemplatePath(String defaultElementTemplatePath) {
    return AgentTestFixtures.AI_AGENT_SUB_PROCESS_V1_ELEMENT_TEMPLATE_PATH;
  }

  @Override
  public Map<String, String> elementTemplateBaselineProperties(
      Map<String, String> defaultProperties) {
    return AgentTestFixtures.AI_AGENT_SUB_PROCESS_V1_ELEMENT_TEMPLATE_PROPERTIES;
  }
}
