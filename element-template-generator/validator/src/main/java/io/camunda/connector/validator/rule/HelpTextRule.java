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
import java.util.Optional;
import java.util.Set;

/**
 * Base for rules that check the {@code description} and {@code tooltip} of properties and groups.
 * Runs only on connectors listed in {@link #CONNECTORS}; add a connector there once its findings
 * are fixed or suppressed.
 */
public abstract class HelpTextRule implements Rule {

  public static final Set<String> CONNECTORS = Set.of("agentic-ai");

  private static final List<String> SECTIONS =
      List.of(ElementTemplate.PROPERTIES, ElementTemplate.GROUPS);
  private static final List<String> FIELDS = List.of(ElementTemplate.DESCRIPTION, "tooltip");

  /** Returns the problem with {@code text}, if any. */
  protected abstract Optional<String> check(String text);

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    if (!appliesTo(file)) {
      return List.of();
    }
    List<Finding> findings = new ArrayList<>();
    for (String section : SECTIONS) {
      JsonNode items = template.path(section);
      for (int i = 0; i < items.size(); i++) {
        for (String field : FIELDS) {
          JsonNode text = items.get(i).path(field);
          if (!text.isTextual()) {
            continue;
          }
          String pointer = "/" + section + "/" + i + "/" + field;
          check(text.asText())
              .ifPresent(message -> findings.add(Finding.error(file, pointer, id(), message)));
        }
      }
    }
    return findings;
  }

  static boolean appliesTo(Path file) {
    for (Path segment : file) {
      if (CONNECTORS.contains(segment.toString())) {
        return true;
      }
    }
    return false;
  }
}
