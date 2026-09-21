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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
class RealProviderSelectionTest {

  @SystemStub private final EnvironmentVariables environment = new EnvironmentVariables();

  @Test
  void shouldPreservePromptCachingOutsideShardedRuns() {
    environment.set("OPENAI_API_KEY", "key");
    environment.set("REAL_LLM_PROVIDER_GROUP", "");

    assertThat(RealProviderApiSmokeSupport.providersWithPromptCaching())
        .extracting(RealProviderApiSmokeSupport.ProviderConfig::label)
        .filteredOn(label -> label.startsWith("openai-") && !label.startsWith("openai-foundry-"))
        .containsExactlyInAnyOrder(
            "openai-responses-v2/gpt-5.5",
            "openai-completions-v2/gpt-5.5",
            "openai-responses-v2/gpt-4.1",
            "openai-completions-v2/gpt-4.1");

    environment.set("REAL_LLM_PROVIDER_GROUP", "openai");

    assertThat(RealProviderApiSmokeSupport.providersWithPromptCaching()).isEmpty();

    environment.set("REAL_LLM_PROVIDER_GROUP", "vertex");

    assertThat(RealProviderApiSmokeSupport.providersWithPromptCaching()).isEmpty();
  }

  @Test
  void shouldFailStrictModeWhenASelectedCapabilityHasNoProviders() {
    environment.set("OPENAI_API_KEY", "key");
    environment.set("REAL_LLM_PROVIDER_GROUP", "openai");
    environment.set("REQUIRE_NATIVE_LLM_PROVIDER", "true");

    assertThatThrownBy(() -> RealProviderApiSmokeSupport.providersWithPromptCaching().toList())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No enabled real provider supports the prompt-caching capability");
  }

  @Test
  void shouldFailStrictModeWhenNoProviderIsEnabled() {
    environment.set("REAL_LLM_PROVIDER_GROUP", "");
    environment.set("REQUIRE_NATIVE_LLM_PROVIDER", "true");

    assertThatThrownBy(() -> RealProviderApiSmokeSupport.providers().toList())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            "No enabled real providers were selected; check provider credentials and "
                + "REAL_LLM_PROVIDER_GROUP");
  }

  @Test
  void shouldDisableVertexGemini37OnlyInShardedRuns() {
    environment.set("GOOGLE_VERTEX_AI_PROJECT_ID", "project");
    environment.set("GOOGLE_VERTEX_AI_REGION", "region");
    environment.set("GOOGLE_VERTEX_AI_SERVICE_ACCOUNT_JSON", "{}");
    environment.set("REAL_LLM_PROVIDER_GROUP", "");

    assertThat(RealProviderApiSmokeSupport.providers())
        .extracting(RealProviderApiSmokeSupport.ProviderConfig::label)
        .contains("google-gemini-vertex-ai-v2/gemini-3.7-flash");

    environment.set("REAL_LLM_PROVIDER_GROUP", "vertex");

    assertThat(RealProviderApiSmokeSupport.providers())
        .extracting(RealProviderApiSmokeSupport.ProviderConfig::label)
        .contains("google-gemini-vertex-ai-v2/gemini-2.5-pro")
        .doesNotContain("google-gemini-vertex-ai-v2/gemini-3.7-flash");
  }

  @Test
  void shouldSelectTheExpectedRowsForEachCapabilitySuite() {
    setAllProviderCredentials();
    environment.set("REAL_LLM_PROVIDER_GROUP", "");

    assertThat(labels(RealProviderApiSmokeSupport.providers()))
        .containsExactlyInAnyOrder(
            "anthropic-v2/claude-sonnet-4-6",
            "anthropic-v2/claude-sonnet-5",
            "anthropic-bedrock-mantle-v2/claude-sonnet-5",
            "bedrock-converse-v2/openai.gpt-oss-120b-1:0",
            "bedrock-converse-v2/global.anthropic.claude-sonnet-5",
            "openai-responses-v2/gpt-5.5",
            "openai-completions-v2/gpt-5.5",
            "openai-responses-v2/gpt-4.1",
            "openai-completions-v2/gpt-4.1",
            "openai-foundry-responses-v2/gpt-5.5",
            "openai-foundry-completions-v2/gpt-5.5",
            "openai-foundry-responses-v2/gpt-4.1",
            "openai-foundry-completions-v2/gpt-4.1",
            "google-gemini-v2/gemini-3.7-flash",
            "google-gemini-vertex-ai-v2/gemini-3.7-flash",
            "google-gemini-v2/gemini-2.5-pro",
            "google-gemini-vertex-ai-v2/gemini-2.5-pro");

    assertThat(labels(RealProviderApiSmokeSupport.providersWithStructuredOutput()))
        .containsExactlyInAnyOrder(
            "anthropic-v2/claude-sonnet-4-6",
            "anthropic-v2/claude-sonnet-5",
            "openai-responses-v2/gpt-5.5",
            "openai-completions-v2/gpt-5.5",
            "openai-responses-v2/gpt-4.1",
            "openai-completions-v2/gpt-4.1",
            "openai-foundry-responses-v2/gpt-5.5",
            "openai-foundry-completions-v2/gpt-5.5",
            "openai-foundry-responses-v2/gpt-4.1",
            "openai-foundry-completions-v2/gpt-4.1",
            "google-gemini-v2/gemini-3.7-flash",
            "google-gemini-vertex-ai-v2/gemini-3.7-flash");

    assertThat(labels(RealProviderApiSmokeSupport.providersWithReasoning()))
        .containsExactlyInAnyOrder(
            "anthropic-v2/claude-sonnet-4-6",
            "anthropic-v2/claude-sonnet-5",
            "anthropic-bedrock-mantle-v2/claude-sonnet-5",
            "bedrock-converse-v2/openai.gpt-oss-120b-1:0",
            "bedrock-converse-v2/global.anthropic.claude-sonnet-5",
            "openai-responses-v2/gpt-5.5",
            "openai-foundry-responses-v2/gpt-5.5",
            "google-gemini-v2/gemini-3.7-flash",
            "google-gemini-vertex-ai-v2/gemini-3.7-flash",
            "google-gemini-v2/gemini-2.5-pro",
            "google-gemini-vertex-ai-v2/gemini-2.5-pro");

    assertThat(labels(RealProviderApiSmokeSupport.providersWithPromptCaching()))
        .containsExactlyInAnyOrder(
            "anthropic-v2/claude-sonnet-4-6",
            "anthropic-v2/claude-sonnet-5",
            "anthropic-bedrock-mantle-v2/claude-sonnet-5",
            "bedrock-converse-v2/global.anthropic.claude-sonnet-5",
            "openai-responses-v2/gpt-5.5",
            "openai-completions-v2/gpt-5.5",
            "openai-responses-v2/gpt-4.1",
            "openai-completions-v2/gpt-4.1",
            "openai-foundry-responses-v2/gpt-5.5",
            "openai-foundry-completions-v2/gpt-5.5",
            "openai-foundry-responses-v2/gpt-4.1",
            "openai-foundry-completions-v2/gpt-4.1",
            "google-gemini-v2/gemini-3.7-flash",
            "google-gemini-vertex-ai-v2/gemini-3.7-flash",
            "google-gemini-v2/gemini-2.5-pro",
            "google-gemini-vertex-ai-v2/gemini-2.5-pro");

    assertThat(labels(RealProviderApiSmokeSupport.providersWithMultimodalUserMessage()))
        .containsExactlyInAnyOrder(
            "anthropic-v2/claude-sonnet-4-6",
            "anthropic-v2/claude-sonnet-5",
            "anthropic-bedrock-mantle-v2/claude-sonnet-5",
            "bedrock-converse-v2/global.anthropic.claude-sonnet-5",
            "openai-responses-v2/gpt-5.5",
            "openai-completions-v2/gpt-5.5",
            "openai-responses-v2/gpt-4.1",
            "openai-completions-v2/gpt-4.1",
            "openai-foundry-responses-v2/gpt-5.5",
            "openai-foundry-completions-v2/gpt-5.5",
            "openai-foundry-responses-v2/gpt-4.1",
            "openai-foundry-completions-v2/gpt-4.1",
            "google-gemini-v2/gemini-3.7-flash",
            "google-gemini-vertex-ai-v2/gemini-3.7-flash",
            "google-gemini-v2/gemini-2.5-pro",
            "google-gemini-vertex-ai-v2/gemini-2.5-pro");
  }

  private void setAllProviderCredentials() {
    environment
        .set("OPENAI_API_KEY", "key")
        .set("OPENAI_FOUNDRY_API_KEY", "key")
        .set("OPENAI_FOUNDRY_ENDPOINT", "https://example.invalid")
        .set("ANTHROPIC_API_KEY", "key")
        .set("ANTHROPIC_BEDROCK_API_KEY", "key")
        .set("AWS_BEDROCK_API_KEY", "key")
        .set("GOOGLE_GEMINI_API_KEY", "key")
        .set("GOOGLE_VERTEX_AI_PROJECT_ID", "project")
        .set("GOOGLE_VERTEX_AI_REGION", "region")
        .set("GOOGLE_VERTEX_AI_SERVICE_ACCOUNT_JSON", "{}");
  }

  private static List<String> labels(Stream<RealProviderApiSmokeSupport.ProviderConfig> rows) {
    return rows.map(RealProviderApiSmokeSupport.ProviderConfig::label).toList();
  }
}
