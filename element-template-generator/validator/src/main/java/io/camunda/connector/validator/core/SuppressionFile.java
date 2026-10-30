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
package io.camunda.connector.validator.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Parses and structurally validates a module's {@value #FILE_NAME}. Semantic checks that need the
 * templates (known rule, matching template, existing property, unused entry) live in {@link
 * SuppressionFilter}.
 */
public final class SuppressionFile {

  public static final String FILE_NAME = "element-template-suppressions.json";
  public static final String RULE_ID = "suppression";

  static final String SUPPRESSIONS = "suppressions";
  static final String RULE = "rule";
  static final String TEMPLATE = "template";
  static final String PROPERTY = "property";
  static final String REASON = "reason";

  private static final Set<String> ENTRY_FIELDS = Set.of(RULE, TEMPLATE, PROPERTY, REASON);

  private SuppressionFile() {}

  public record Result(List<Suppression> suppressions, List<Finding> findings) {}

  public static Result load(Path file) {
    TemplateLoader.Result loaded = TemplateLoader.load(file);
    if (!loaded.ok()) {
      return new Result(List.of(), List.of(loaded.finding()));
    }
    return parse(file, loaded.node());
  }

  static Result parse(Path file, JsonNode root) {
    List<Suppression> suppressions = new ArrayList<>();
    List<Finding> findings = new ArrayList<>();

    if (!root.isObject() || root.size() != 1 || !root.path(SUPPRESSIONS).isArray()) {
      findings.add(
          error(file, "", "Expected an object with a single \"" + SUPPRESSIONS + "\" array."));
      return new Result(suppressions, findings);
    }

    Set<List<String>> seen = new HashSet<>();
    JsonNode entries = root.get(SUPPRESSIONS);
    for (int i = 0; i < entries.size(); i++) {
      String pointer = "/" + SUPPRESSIONS + "/" + i;
      JsonNode entry = entries.get(i);
      if (!entry.isObject()) {
        findings.add(error(file, pointer, "Suppression must be an object."));
        continue;
      }
      int before = findings.size();

      for (Iterator<String> it = entry.fieldNames(); it.hasNext(); ) {
        String name = it.next();
        if (!ENTRY_FIELDS.contains(name)) {
          findings.add(
              error(
                  file,
                  pointer + "/" + JsonPointers.escape(name),
                  "Unknown field \""
                      + name
                      + "\". Allowed: "
                      + RULE
                      + ", "
                      + TEMPLATE
                      + ", "
                      + PROPERTY
                      + ", "
                      + REASON
                      + "."));
        }
      }
      String rule = text(file, pointer, entry, RULE, true, findings);
      String template = text(file, pointer, entry, TEMPLATE, true, findings);
      String property = text(file, pointer, entry, PROPERTY, false, findings);
      String reason = text(file, pointer, entry, REASON, true, findings);

      PathMatcher matcher = null;
      if (template != null) {
        try {
          matcher = FileSystems.getDefault().getPathMatcher("glob:" + template);
        } catch (IllegalArgumentException e) {
          findings.add(error(file, pointer + "/" + TEMPLATE, "Invalid glob: " + e.getMessage()));
        }
      }
      if (findings.size() > before) {
        continue;
      }
      if (!seen.add(Arrays.asList(rule, template, property))) {
        findings.add(
            error(
                file, pointer, "Duplicate suppression for the same rule, template, and property."));
        continue;
      }
      suppressions.add(new Suppression(file, pointer, rule, template, property, reason, matcher));
    }
    return new Result(suppressions, findings);
  }

  private static String text(
      Path file,
      String pointer,
      JsonNode entry,
      String field,
      boolean required,
      List<Finding> findings) {
    JsonNode node = entry.get(field);
    if (node == null) {
      if (required) {
        findings.add(error(file, pointer, "Missing required field \"" + field + "\"."));
      }
      return null;
    }
    if (!node.isTextual() || node.asText().isBlank()) {
      findings.add(
          error(file, pointer + "/" + field, "\"" + field + "\" must be a non-blank string."));
      return null;
    }
    return node.asText();
  }

  static Finding error(Path file, String pointer, String message) {
    return Finding.error(file, pointer, RULE_ID, message);
  }
}
