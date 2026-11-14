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

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.validator.core.Finding;
import io.camunda.connector.validator.core.Rule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AiModelInputRule implements Rule {

  private static final Set<String> MODEL_LABELS = Set.of("Model", "Model name", "Model ID");
  private static final Map<String, String> ALIGNED_INPUTS =
      Map.of(
          "io.camunda.connectors.OpenAI.v1", "internal_model",
          "io.camunda.connectors.GoogleGemini.v1", "input.model",
          "io.camunda.connectors.EmbeddingsVectorDB.v1",
              "embeddingModelProvider.bedrock.modelName");

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    String modelInput =
        ALIGNED_INPUTS.get(template.path("id").asText("").replaceFirst("-hybrid$", ""));
    boolean foundModelInput = false;
    List<Finding> findings = new ArrayList<>();
    JsonNode properties = template.path("properties");
    for (int i = 0; i < properties.size(); i++) {
      JsonNode property = properties.get(i);
      boolean aligned =
          modelInput != null && modelInput.equals(property.path("binding").path("name").asText());
      foundModelInput |= aligned;
      if (!aligned && !MODEL_LABELS.contains(property.path("label").asText())) {
        continue;
      }
      String pointer = "/properties/" + i;
      String type = property.path("type").asText();
      if (!Set.of("String", "Text").contains(type) || property.has("choices")) {
        findings.add(
            Finding.error(
                file, pointer, id(), "Model IDs must use String or Text inputs without choices."));
      }
      if (aligned) {
        JsonNode placeholder = property.path("placeholder");
        if (!placeholder.isTextual() || placeholder.asText().isBlank()) {
          findings.add(
              Finding.error(file, pointer + "/placeholder", id(), "Provide an example model ID."));
        }
        if (property.has("value")
            && (!property.path("value").isTextual()
                || !property.path("value").asText().isEmpty())) {
          findings.add(
              Finding.error(file, pointer + "/value", id(), "Model IDs must not have a default."));
        }
        if (!"optional".equals(property.path("feel").asText())
            || !property.path("constraints").path("notEmpty").asBoolean()) {
          findings.add(
              Finding.error(
                  file, pointer, id(), "Model IDs must be required with optional FEEL support."));
        }
      }
    }
    if (modelInput != null && !foundModelInput) {
      findings.add(
          Finding.error(file, "/properties", id(), "Missing model input: " + modelInput + "."));
    }
    return findings;
  }
}
