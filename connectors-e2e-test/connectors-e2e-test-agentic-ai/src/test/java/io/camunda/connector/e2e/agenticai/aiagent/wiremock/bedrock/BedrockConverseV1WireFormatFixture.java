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
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.function.Function;

/**
 * Plugs AWS Bedrock's Converse API wire format into the provider-agnostic {@code
 * ProviderWireFormatFixture} SPI, driving the connector through the v1 element template. With the
 * v1&rarr;v2 provider-config rewrite switch on (the default), the v1 template's {@code
 * provider.bedrock.*} config is rewritten onto the native v2 Bedrock provider at the connector
 * boundary, so - just like {@link BedrockConverseV2WireFormatFixture} - the connector always calls
 * the AWS SDK's async {@code converseStream} operation, which expects a real AWS EventStream binary
 * body at {@code POST /model/test-model/converse-stream}. {@link #stubConversation(TurnStub...)} is
 * therefore overridden here to stub that via {@link
 * StreamingBedrockConverseEventStreamChatModelStubs}, exactly like the v2 fixture, rather than
 * inheriting {@link AbstractBedrockConverseWireFormatFixture}'s implicit buffered-JSON default
 * (which matched the pre-rewrite v1 client).
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
}
