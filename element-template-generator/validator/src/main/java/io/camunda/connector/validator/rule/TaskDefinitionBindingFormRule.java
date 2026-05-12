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
import java.util.List;

/**
 * The legacy binding type {@code zeebe:taskDefinition:type} must not be used; the canonical form is
 * {@code zeebe:taskDefinition} with {@code property: "type"} (and likewise for other taskDefinition
 * properties such as {@code retries}).
 *
 * <p>The element-template JSON schema currently accepts both spellings without warning, but the
 * generator and all newer templates use the canonical form. Hand-written templates that copy older
 * patterns can still drift to the legacy form — this rule prevents that.
 */
public class TaskDefinitionBindingFormRule implements Rule {
  private static final String LEGACY_BINDING_TYPE = "zeebe:taskDefinition:type";

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    JsonNode properties = template.path(ElementTemplate.PROPERTIES);
    if (!properties.isArray()) {
      return List.of();
    }
    List<Finding> findings = new ArrayList<>();
    for (int i = 0; i < properties.size(); i++) {
      JsonNode binding = properties.get(i).path(ElementTemplate.BINDING);
      if (!binding.isObject()) {
        continue;
      }
      JsonNode bindingType = binding.path(ElementTemplate.TYPE);
      if (bindingType.isTextual() && LEGACY_BINDING_TYPE.equals(bindingType.asText())) {
        findings.add(
            Finding.error(
                file,
                "/properties/" + i + "/binding/type",
                id(),
                "Legacy binding type \""
                    + LEGACY_BINDING_TYPE
                    + "\" is not allowed. Use { \"type\": \"zeebe:taskDefinition\", \"property\":"
                    + " \"type\" } instead."));
      }
    }
    return findings;
  }
}
