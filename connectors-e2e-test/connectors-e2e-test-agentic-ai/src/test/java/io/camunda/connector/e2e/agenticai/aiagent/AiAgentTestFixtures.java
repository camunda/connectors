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
package io.camunda.connector.e2e.agenticai.aiagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowingConsumer;

public interface AiAgentTestFixtures {
  String AI_AGENT_ELEMENT_TEMPLATE_PATH =
      "../../connectors/agentic-ai/element-templates/agenticai-aiagent-outbound-connector.json";

  String AD_HOC_TOOLS_SCHEMA_ELEMENT_TEMPLATE_PATH =
      "../../connectors/agentic-ai/element-templates/agenticai-adhoctoolsschema-outbound-connector.json";

  String AI_AGENT_TASK_ID = "AI_Agent";
  Map<String, String> AI_AGENT_ELEMENT_TEMPLATE_PROPERTIES =
      Map.ofEntries(
          Map.entry("provider.type", "openai"),
          Map.entry("provider.openai.authentication.apiKey", "DUMMY_API_KEY"),
          Map.entry("provider.openai.model.model", "gpt-4o"),
          Map.entry(
              "data.systemPrompt.prompt",
              "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking."),
          Map.entry(
              "data.userPrompt.prompt",
              "=if (is defined(followUpUserPrompt)) then followUpUserPrompt else userPrompt"),
          Map.entry(
              "data.userPrompt.documents",
              "=if (is defined(followUpUserPrompt)) then [] else downloadedFiles"),
          Map.entry("data.tools.containerElementId", "Agent_Tools"),
          Map.entry("data.tools.toolCallResults", "=toolCallResults"),
          Map.entry("data.memory.storage.type", "in-process"),
          Map.entry("data.memory.contextWindowSize", "=50"),
          Map.entry("data.response.includeAssistantMessage", "=true"),
          Map.entry("retryCount", "3"),
          Map.entry("retryBackoff", "PT2S"));

  String HAIKU_TEXT = "Endless waves whisper | moonlight dances on the tide | secrets drift below.";
  String HAIKU_JSON =
      "{\"text\":\"%s\", \"length\": %d}".formatted(HAIKU_TEXT, HAIKU_TEXT.length());
  ThrowingConsumer<Object> HAIKU_JSON_ASSERTIONS =
      json ->
          assertThat(json)
              .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
              .containsExactly(entry("text", HAIKU_TEXT), entry("length", HAIKU_TEXT.length()));

  String FEEDBACK_LOOP_RESPONSE_TEXT =
      "A very complex calculation only the superflux calculation tool can do.";
}
