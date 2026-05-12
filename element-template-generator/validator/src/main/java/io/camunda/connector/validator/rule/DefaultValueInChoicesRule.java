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
import io.camunda.connector.validator.core.ElementTemplate;
import io.camunda.connector.validator.core.Finding;
import io.camunda.connector.validator.core.Rule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * For Dropdown properties that declare both a default {@code value} and a {@code choices} list, the
 * default must be one of the declared choices.
 */
public class DefaultValueInChoicesRule implements Rule {

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    JsonNode properties = template.path(ElementTemplate.PROPERTIES);
    if (!properties.isArray()) {
      return List.of();
    }
    List<Finding> findings = new ArrayList<>();
    for (int i = 0; i < properties.size(); i++) {
      JsonNode property = properties.get(i);
      JsonNode type = property.path(ElementTemplate.TYPE);
      JsonNode defaultValue = property.path(ElementTemplate.VALUE);
      JsonNode choices = property.path(ElementTemplate.CHOICES);
      if (!"Dropdown".equals(type.asText("")) || !defaultValue.isTextual() || !choices.isArray()) {
        continue;
      }
      Set<String> choiceValues = new HashSet<>();
      for (JsonNode c : choices) {
        JsonNode v = c.path(ElementTemplate.VALUE);
        if (v.isTextual()) {
          choiceValues.add(v.asText());
        }
      }
      if (!choiceValues.isEmpty() && !choiceValues.contains(defaultValue.asText())) {
        String propertyId = property.path(ElementTemplate.ID).asText("");
        findings.add(
            Finding.error(
                file,
                "/properties/" + i + "/value",
                id(),
                "Default value \""
                    + defaultValue.asText()
                    + "\" of property \""
                    + propertyId
                    + "\" is not one of its declared choices."));
      }
    }
    return findings;
  }
}
