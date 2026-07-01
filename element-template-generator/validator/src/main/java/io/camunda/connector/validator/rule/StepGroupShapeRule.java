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
 * Validates group step nodes (intermediate nodes that carry a {@code steps} child array). A group
 * must have a non-empty {@code steps[]}; {@code description} is optional; {@code keywords} are
 * disallowed (keywords are conceptually leaf-only — search aliases apply to operations, not to
 * categories); {@code presetId} is disallowed (only leaves point at presets).
 */
public class StepGroupShapeRule implements Rule {

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    if (OperationMetadataIgnoreList.isIgnored(file)) {
      return List.of();
    }
    JsonNode steps = template.path(ElementTemplate.STEPS);
    if (!steps.isArray()) {
      return List.of();
    }
    List<Finding> findings = new ArrayList<>();
    for (int i = 0; i < steps.size(); i++) {
      walk(steps.get(i), "/steps/" + i, file, findings);
    }
    return findings;
  }

  private void walk(JsonNode node, String pointer, Path file, List<Finding> findings) {
    if (!node.isObject()) {
      return;
    }
    JsonNode children = node.path(ElementTemplate.STEPS);
    boolean isGroup = children.isArray();
    if (!isGroup) {
      return;
    }
    if (children.isEmpty()) {
      findings.add(
          Finding.error(
              file, pointer + "/steps", id(), "Group step must have a non-empty \"steps\" array."));
    }
    if (node.has(ElementTemplate.KEYWORDS)) {
      findings.add(
          Finding.error(
              file,
              pointer + "/keywords",
              id(),
              "Group step must not declare \"keywords\" — keywords are leaf-only."));
    }
    if (node.has(ElementTemplate.PRESET_ID)) {
      findings.add(
          Finding.error(
              file,
              pointer + "/presetId",
              id(),
              "Group step must not declare \"presetId\" — only leaf steps reference presets."));
    }
    for (int i = 0; i < children.size(); i++) {
      walk(children.get(i), pointer + "/steps/" + i, file, findings);
    }
  }
}
