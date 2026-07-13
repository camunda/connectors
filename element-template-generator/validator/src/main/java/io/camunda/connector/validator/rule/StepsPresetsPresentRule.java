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
import io.camunda.connector.validator.core.OperationMetadataIgnoreList;
import io.camunda.connector.validator.core.Rule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The operations-metadata contract requires every connector template to declare both {@code steps}
 * and {@code presets} at the root, and both must be non-empty arrays. Connectors listed in {@link
 * OperationMetadataIgnoreList} are exempt until their {@code @Searchable} annotations or
 * hand-authored JSON sections land.
 */
public class StepsPresetsPresentRule implements Rule {

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    if (OperationMetadataIgnoreList.isIgnored(file, template)) {
      return List.of();
    }
    List<Finding> findings = new ArrayList<>();
    checkArray(file, template, ElementTemplate.STEPS, findings);
    checkArray(file, template, ElementTemplate.PRESETS, findings);
    return findings;
  }

  private void checkArray(Path file, JsonNode template, String field, List<Finding> findings) {
    JsonNode node = template.path(field);
    if (node.isMissingNode() || node.isNull()) {
      findings.add(
          Finding.error(file, "/" + field, id(), "Required field \"" + field + "\" is missing."));
      return;
    }
    if (!node.isArray()) {
      findings.add(
          Finding.error(file, "/" + field, id(), "Field \"" + field + "\" must be an array."));
      return;
    }
    if (node.isEmpty()) {
      findings.add(
          Finding.error(file, "/" + field, id(), "Field \"" + field + "\" must be non-empty."));
    }
  }
}
