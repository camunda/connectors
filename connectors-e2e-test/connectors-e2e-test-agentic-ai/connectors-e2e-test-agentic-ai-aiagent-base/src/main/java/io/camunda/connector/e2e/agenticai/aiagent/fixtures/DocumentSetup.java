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
package io.camunda.connector.e2e.agenticai.aiagent.fixtures;

import io.camunda.connector.e2e.ElementTemplate;
import java.util.Map;
import java.util.function.Function;

/**
 * How the AI Agent of a test gives documents to the model: the checkbox "Use document references"
 * ({@code data.documents.references}) and the user prompt delivery ({@code
 * data.documents.userPromptDelivery}). Apply it to the v2 element template of the test.
 *
 * <p>{@link #REFERENCES_ON} and {@link #USER_PROMPT_CONTENT} keep the default of the checkbox,
 * which is on. The template sets it as a Boolean, and its delivery dropdown shows only while it is
 * {@code true}.
 */
public enum DocumentSetup implements Function<ElementTemplate, ElementTemplate> {

  /**
   * "Use document references" cleared: the model gets the content of every document, no handle and
   * no built-in document tool. The delivery dropdown is hidden, so the element has no delivery.
   */
  REFERENCES_OFF(Map.of("data.documents.references", "false")),

  /**
   * "Use document references" selected with the delivery "Reference" for the user prompt: the model
   * gets the handle of every document.
   */
  REFERENCES_ON(Map.of("data.documents.userPromptDelivery", "reference")),

  /**
   * "Use document references" selected with the delivery "Content" for the user prompt: the model
   * gets the handle and the content of a document of the user prompt, and the handle of every other
   * document.
   */
  USER_PROMPT_CONTENT(Map.of("data.documents.userPromptDelivery", "content"));

  private final Map<String, String> properties;

  DocumentSetup(Map<String, String> properties) {
    this.properties = properties;
  }

  /** The template properties of the setup, by property id. */
  public Map<String, String> properties() {
    return properties;
  }

  /** Whether a new agent instance tracks its documents, the {@code documents} feature. */
  public boolean tracksDocuments() {
    return this != REFERENCES_OFF;
  }

  @Override
  public ElementTemplate apply(ElementTemplate elementTemplate) {
    properties.forEach(elementTemplate::property);
    return elementTemplate;
  }
}
