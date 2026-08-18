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

import io.camunda.connector.e2e.ElementTemplate;
import java.util.function.Function;

/**
 * Shared foundation for native-OpenAI-only v2 sub-process e2e coverage, scoped to the Responses API
 * family - the sibling of {@link BaseOpenAiCompletionsSubProcessTest} for Chat Completions.
 */
abstract class BaseOpenAiResponsesSubProcessTest extends BaseAgentSubProcessV2Test {

  private static final String DEFAULT_MODEL = "test-model";

  @Override
  protected Function<ElementTemplate, ElementTemplate> providerConfigurer() {
    return this::configureOpenAiResponsesBackend;
  }

  /**
   * Model id to configure alongside the backend wiring. Override to fix a different model for the
   * whole test class, or leave the default when a test doesn't care which model is used.
   */
  protected String defaultModel() {
    return DEFAULT_MODEL;
  }

  private ElementTemplate configureOpenAiResponsesBackend(ElementTemplate template) {
    return template
        .property("provider.type", "openai")
        .property("provider.openai.api.type", "responses")
        .property("provider.openai.backend.type", "openai-api")
        .property("provider.openai.backend.openai.endpoint", wireMock.getHttpBaseUrl() + "/v1")
        .property("provider.openai.backend.openai.apiKey", "dummy")
        .property("provider.openai.model.model", defaultModel());
  }
}
