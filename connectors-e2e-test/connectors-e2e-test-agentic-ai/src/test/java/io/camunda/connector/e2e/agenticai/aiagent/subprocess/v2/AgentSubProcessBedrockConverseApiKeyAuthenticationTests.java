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
package io.camunda.connector.e2e.agenticai.aiagent.subprocess.v2;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.bedrock.StreamingBedrockConverseEventStreamChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Proves that {@code AwsApiKeyAuthentication} actually reaches the wire as a bearer token via the
 * AWS SDK's native {@code httpBearerAuth} scheme (see {@code BedrockConverseChatModelFactory}),
 * rather than being silently dropped in favor of the sigv4 scheme the SDK lists first by default.
 */
class AgentSubProcessBedrockConverseApiKeyAuthenticationTests
    extends BaseBedrockConverseNativeSubProcessTest {

  @Override
  protected Function<ElementTemplate, ElementTemplate> providerConfigurer() {
    return template ->
        template
            .property("provider.type", "bedrock")
            .property("provider.bedrock.region", "us-east-1")
            .property("provider.bedrock.endpoint", wireMock.getHttpBaseUrl())
            .property("provider.bedrock.authentication.type", "apiKey")
            .property("provider.bedrock.authentication.apiKey", "bedrock-api-key")
            .property("provider.bedrock.model.model", defaultModel());
  }

  @Test
  void sendsApiKeyAsBearerAuthorizationHeader() throws Exception {
    StreamingBedrockConverseEventStreamChatModelStubs.stubConversation(
        TurnStub.text("Bearer-authenticated response", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", "Hello")));

    final var request = soleRecordedRequest();
    assertThat(request.getHeader("Authorization")).isEqualTo("Bearer bedrock-api-key");
  }
}
