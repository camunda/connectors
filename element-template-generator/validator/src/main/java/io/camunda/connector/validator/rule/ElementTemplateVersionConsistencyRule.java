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
import io.camunda.connector.validator.core.MultiFileRule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * When a template declares a property bound to the {@code elementTemplateVersion} task header
 * ({@code binding.key == "elementTemplateVersion"}, {@code binding.type == "zeebe:taskHeader"}),
 * that property's {@code value} must equal the template's top-level {@code version} field rendered
 * as a string. In practice this property is a {@code Hidden} one, but the rule keys off the binding
 * alone so the check does not depend on the property's presentation type.
 *
 * <p>Hand-authored element-template-only connectors repeatedly drifted here: the top-level {@code
 * version} got bumped while the header value lagged behind (e.g. top=6 / hidden=4). ETG-generated
 * templates keep the two in sync, but nothing enforced it for hand-written ones.
 *
 * <p>The rule only fires when the bound property is present — connectors that never declare it are
 * left alone rather than forced into a migration. It runs on {@code versioned/} snapshots too,
 * where the value must equal the snapshot's own top-level {@code version}, consistent with how
 * {@link VersionedTemplateConsistencyRule} treats the {@code version} field.
 */
public class ElementTemplateVersionConsistencyRule implements MultiFileRule {

  private static final String ELEMENT_TEMPLATE_VERSION_HEADER = "elementTemplateVersion";
  private static final String TASK_HEADER_BINDING_TYPE = "zeebe:taskHeader";
  private static final String KEY = "key";

  @Override
  public List<Finding> apply(Map<Path, JsonNode> templates) {
    List<Finding> findings = new ArrayList<>();
    for (Map.Entry<Path, JsonNode> entry : templates.entrySet()) {
      Path path = entry.getKey();
      JsonNode template = entry.getValue();
      JsonNode properties = template.path(ElementTemplate.PROPERTIES);
      if (!properties.isArray()) {
        continue;
      }
      for (int i = 0; i < properties.size(); i++) {
        JsonNode property = properties.get(i);
        JsonNode binding = property.path(ElementTemplate.BINDING);
        if (!isElementTemplateVersionHeader(binding)) {
          continue;
        }
        JsonNode versionNode = template.path(ElementTemplate.VERSION);
        if (!versionNode.isNumber()) {
          // A missing/non-numeric top-level version is a separate concern handled by other rules;
          // there is nothing to compare against here.
          continue;
        }
        String expected = String.valueOf(versionNode.asInt());
        String actual = property.path(ElementTemplate.VALUE).asText(null);
        if (!expected.equals(actual)) {
          findings.add(
              Finding.error(
                  path,
                  "/properties/" + i + "/value",
                  id(),
                  "Hidden \""
                      + ELEMENT_TEMPLATE_VERSION_HEADER
                      + "\" header value is \""
                      + (actual == null ? "<missing>" : actual)
                      + "\" but the template version is "
                      + expected
                      + "; they must match."));
        }
      }
    }
    return findings;
  }

  private static boolean isElementTemplateVersionHeader(JsonNode binding) {
    return binding.isObject()
        && TASK_HEADER_BINDING_TYPE.equals(binding.path(ElementTemplate.TYPE).asText(null))
        && ELEMENT_TEMPLATE_VERSION_HEADER.equals(binding.path(KEY).asText(null));
  }
}
