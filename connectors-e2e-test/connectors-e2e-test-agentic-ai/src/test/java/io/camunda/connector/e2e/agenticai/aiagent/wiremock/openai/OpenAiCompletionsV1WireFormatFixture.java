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
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ProviderWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedChatRequest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.List;
import java.util.function.Function;

/**
 * Plugs the OpenAI Chat Completions stubs ({@link OpenAiCompletionsChatModelStubs} / {@link
 * OpenAiCompletionsRecordedConversation}, which also back the rest of the agentic-ai e2e suite)
 * into the provider-agnostic {@link ProviderWireFormatFixture} SPI. Drives the v1 {@code
 * openaiCompatible} element template, so with the v1→v2 rewrite switch on (the default) this row
 * proves a v1 provider config is routed onto the native provider's wire.
 */
public final class OpenAiCompletionsV1WireFormatFixture implements ProviderWireFormatFixture {

  @Override
  public String apiName() {
    return "OpenAiCompletionsV1";
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
            .property("provider.type", "openaiCompatible")
            .property("provider.openaiCompatible.endpoint", wireMock.getHttpBaseUrl() + "/v1")
            .property("provider.openaiCompatible.authentication.apiKey", "dummy")
            .property("provider.openaiCompatible.model.model", "test-model");
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
