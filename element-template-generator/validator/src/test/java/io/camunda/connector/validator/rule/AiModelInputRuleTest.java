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
package io.camunda.connector.validator.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.validator.core.Finding;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AiModelInputRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path FILE = Path.of("connectors/openai/element-templates/t.json");
  private final AiModelInputRule rule = new AiModelInputRule();

  @Test
  void validatesThePublishedOpenAiTemplateAndKeepsThePreviousVersion() throws Exception {
    var directory =
        Path.of("..", "..", "connectors", "openai", "element-templates").toAbsolutePath();
    var currentFile = directory.resolve("openai-connector.json");
    var current = MAPPER.readTree(Files.readString(currentFile));
    var previous =
        MAPPER.readTree(Files.readString(directory.resolve("versioned/openai-connector-8.json")));

    assertThat(current.path("version").asInt()).isEqualTo(9);
    assertThat(previous.path("version").asInt()).isEqualTo(8);
    assertThat(rule.apply(currentFile, current)).isEmpty();
    assertThat(rule.apply(currentFile, previous)).isNotEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"String", "Text"})
  void acceptsFreeTextWithAnExampleAndNoDefault(String type) throws Exception {
    var template = validTemplate();
    model(template).put("type", type);
    assertThat(rule.apply(FILE, template)).isEmpty();
    model(template).remove("value");
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "io.camunda.connectors.OpenAI.v1,internal_model",
    "io.camunda.connectors.GoogleGemini.v1,input.model",
    "io.camunda.connectors.GoogleGemini.v1-hybrid,input.model",
    "io.camunda.connectors.EmbeddingsVectorDB.v1,embeddingModelProvider.bedrock.modelName",
    "io.camunda.connectors.EmbeddingsVectorDB.v1-hybrid,embeddingModelProvider.bedrock.modelName"
  })
  void requiresExamplesForEveryAlignedConnector(String id, String binding) throws Exception {
    var template = validTemplate();
    template.put("id", id);
    ((ObjectNode) model(template).path("binding")).put("name", binding);
    model(template).remove("placeholder");
    assertThat(rule.apply(FILE, template))
        .extracting(Finding::jsonPointer)
        .containsExactly("/properties/0/placeholder");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Dropdown", "Hidden", "Number"})
  void rejectsNonTextInputs(String type) throws Exception {
    var template = validTemplate();
    model(template).put("type", type);
    assertThat(rule.apply(FILE, template)).hasSize(1);
  }

  @Test
  void rejectsChoicesEvenOnAString() throws Exception {
    var template = validTemplate();
    model(template).putArray("choices");
    assertThat(rule.apply(FILE, template)).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  void rejectsEmptyExamples(String placeholder) throws Exception {
    var template = validTemplate();
    model(template).put("placeholder", placeholder);
    assertThat(rule.apply(FILE, template)).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"gpt-4o", "= \"gpt-4o\"", " "})
  void rejectsDefaults(String value) throws Exception {
    var template = validTemplate();
    model(template).put("value", value);
    assertThat(rule.apply(FILE, template)).hasSize(1);
  }

  @Test
  void requiresOptionalFeelAndNotEmpty() throws Exception {
    var template = validTemplate();
    model(template).remove("feel");
    assertThat(rule.apply(FILE, template)).hasSize(1);
    model(template).put("feel", "optional");
    model(template).remove("constraints");
    assertThat(rule.apply(FILE, template)).hasSize(1);
  }

  @Test
  void requiresTheModelPropertyEvenIfItIsRemoved() throws Exception {
    var template = validTemplate();
    template.putArray("properties");
    assertThat(rule.apply(FILE, template)).hasSize(1);
  }

  @Test
  void guardsOtherModelSelectorsButAllowsProviderEnums() throws Exception {
    var template = validTemplate();
    template.put("id", "another.ai.connector");
    model(template).put("type", "Dropdown");
    assertThat(rule.apply(FILE, template)).hasSize(1);
    model(template).put("label", "Model provider");
    assertThat(rule.apply(FILE, template)).isEmpty();
    model(template).put("label", "Model type");
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  private ObjectNode validTemplate() throws Exception {
    return (ObjectNode)
        MAPPER.readTree(
            """
            {
              "id": "io.camunda.connectors.OpenAI.v1",
              "properties": [{
                "label": "Model",
                "type": "String",
                "feel": "optional",
                "value": "",
                "placeholder": "example-model",
                "binding": {"type": "zeebe:input", "name": "internal_model"},
                "constraints": {"notEmpty": true}
              }]
            }
            """);
  }

  private ObjectNode model(ObjectNode template) {
    return (ObjectNode) template.path("properties").get(0);
  }
}
