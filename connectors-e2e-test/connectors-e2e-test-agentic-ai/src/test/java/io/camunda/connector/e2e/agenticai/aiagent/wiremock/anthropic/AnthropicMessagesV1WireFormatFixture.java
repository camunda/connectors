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
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ProviderWireFormatFixture;
import java.util.function.Function;

/**
 * Plugs Anthropic's Messages API wire format into the provider-agnostic {@link
 * ProviderWireFormatFixture} SPI, driving the connector through the v1 element template. With the
 * v1&rarr;v2 provider-config rewrite switch on (the default), the v1 template's {@code
 * provider.anthropic.*} config is rewritten onto the native v2 Anthropic provider at the connector
 * boundary, so - just like {@link AnthropicMessagesV2WireFormatFixture} - the connector always
 * calls the vendor SDK's streaming endpoint ({@code client.messages().createStreaming(params)}),
 * which expects a real {@code text/event-stream} SSE body - the same streamed response {@link
 * AbstractAnthropicMessagesWireFormatFixture} stubs via {@link
 * StreamingAnthropicMessagesSseChatModelStubs} for every row of this suite.
 *
 * <p>See {@link AbstractAnthropicMessagesWireFormatFixture} for the *request* wire-format plumbing
 * shared with the v2 fixture ({@code recordedRequests()}, {@code
 * assertResponseFormatConfigured(...)}) - identical between v1 and v2 since only the response
 * framing differs.
 */
public final class AnthropicMessagesV1WireFormatFixture
    extends AbstractAnthropicMessagesWireFormatFixture {

  @Override
  public String apiName() {
    return "AnthropicMessagesV1";
  }

  @Override
  public Function<ElementTemplate, ElementTemplate> configureProvider(
      WireMockRuntimeInfo wireMock) {
    return template ->
        template
            .property("provider.type", "anthropic")
            .property("provider.anthropic.endpoint", wireMock.getHttpBaseUrl() + "/v1/")
            .property("provider.anthropic.authentication.apiKey", "dummy")
            .property("provider.anthropic.model.model", "test-model");
  }
}
