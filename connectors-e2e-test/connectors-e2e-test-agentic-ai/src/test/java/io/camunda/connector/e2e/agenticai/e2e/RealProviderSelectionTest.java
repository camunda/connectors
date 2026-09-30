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
package io.camunda.connector.e2e.agenticai.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
class RealProviderSelectionTest {

  @SystemStub private final EnvironmentVariables environment = new EnvironmentVariables();

  @Test
  void shouldPreserveOpenAiPromptCachingOutsideShardedRuns() {
    environment.set("OPENAI_API_KEY", "key");
    environment.set("REAL_LLM_PROVIDER_GROUP", "");

    assertThat(RealProviderApiSmokeIT.providersWithPromptCaching())
        .extracting(RealProviderApiSmokeIT.ProviderConfig::label)
        .filteredOn(label -> label.startsWith("openai-") && !label.startsWith("openai-foundry-"))
        .containsExactlyInAnyOrder(
            "openai-responses-v2/gpt-5.5",
            "openai-completions-v2/gpt-5.5",
            "openai-responses-v2/gpt-4.1",
            "openai-completions-v2/gpt-4.1");

    environment.set("REAL_LLM_PROVIDER_GROUP", "openai");

    assertThat(RealProviderApiSmokeIT.providersWithPromptCaching()).isEmpty();
  }

  @Test
  void shouldDisableVertexGemini37OnlyInShardedRuns() {
    environment.set("GOOGLE_VERTEX_AI_PROJECT_ID", "project");
    environment.set("GOOGLE_VERTEX_AI_REGION", "region");
    environment.set("GOOGLE_VERTEX_AI_SERVICE_ACCOUNT_JSON", "{}");
    environment.set("REAL_LLM_PROVIDER_GROUP", "");

    assertThat(RealProviderApiSmokeIT.providers())
        .extracting(RealProviderApiSmokeIT.ProviderConfig::label)
        .contains("google-gemini-vertex-ai-v2/gemini-3.7-flash");

    environment.set("REAL_LLM_PROVIDER_GROUP", "vertex");

    assertThat(RealProviderApiSmokeIT.providers())
        .extracting(RealProviderApiSmokeIT.ProviderConfig::label)
        .contains("google-gemini-vertex-ai-v2/gemini-2.5-pro")
        .doesNotContain("google-gemini-vertex-ai-v2/gemini-3.7-flash");
  }
}
