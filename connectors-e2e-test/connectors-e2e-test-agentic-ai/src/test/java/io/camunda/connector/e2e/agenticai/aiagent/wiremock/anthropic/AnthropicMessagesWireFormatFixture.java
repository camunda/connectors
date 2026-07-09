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
 * ProviderWireFormatFixture} SPI, driving the connector through its langchain4j-bridge Anthropic
 * provider (v1). See {@link AbstractAnthropicMessagesWireFormatFixture} for the wire-format
 * plumbing shared with the native provider fixture ({@link
 * NativeAnthropicMessagesWireFormatFixture}).
 */
public final class AnthropicMessagesWireFormatFixture
    extends AbstractAnthropicMessagesWireFormatFixture {

  @Override
  public String apiName() {
    return "AnthropicMessages";
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
