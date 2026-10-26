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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes findings covered by a module's {@value SuppressionFile#FILE_NAME} and reports problems
 * with the suppressions themselves: unknown rule, no matching template, unknown property, and
 * entries that no longer suppress anything.
 *
 * <p>The suppression file sits in the module root, next to its {@code element-templates/}
 * directory. Template globs are relative to that directory.
 */
public final class SuppressionFilter {

  private static final String ELEMENT_TEMPLATES = "element-templates";
  private static final Pattern PROPERTY_POINTER = Pattern.compile("^/properties/(\\d+)(?:/.*)?$");

  private SuppressionFilter() {}

  public record Result(List<Finding> findings, int suppressed) {}

  /**
   * @param suppressionFiles every suppression file in the scan scope, including those of modules
   *     without templates, so their entries are still validated
   * @param skippedDirs directory names pruned during discovery; a module with such a directory
   *     under its element-templates/ is only partially scanned
   * @param suppressibleRules rule ids that may be suppressed; anything else is rejected
   */
  public static Result apply(
      List<Finding> findings,
      Map<Path, JsonNode> templates,
      List<Path> suppressionFiles,
      Set<String> skippedDirs,
      Set<String> suppressibleRules) {
    Map<Path, List<Path>> templatesByDir = new LinkedHashMap<>();
    for (Path template : templates.keySet()) {
      elementTemplatesDir(template)
          .ifPresent(
              dir -> templatesByDir.computeIfAbsent(dir, k -> new ArrayList<>()).add(template));
    }

    // A scan root inside a module (its element-templates/ or hybrid/) or a skipped directory under
    // element-templates/ hides part of the module's templates, so its suppressions still apply but
    // scope-dependent checks are skipped.
    Set<Path> files = new LinkedHashSet<>(suppressionFiles);
    Set<Path> partialScope = new LinkedHashSet<>();
    for (Path file : suppressionFiles) {
      if (TemplateFinder.containsSkippedDirectory(
          file.resolveSibling(ELEMENT_TEMPLATES), skippedDirs)) {
        partialScope.add(file);
      }
    }
    for (Path dir : templatesByDir.keySet()) {
      Path file = dir.resolveSibling(SuppressionFile.FILE_NAME);
      if (!files.contains(file) && Files.isRegularFile(file)) {
        files.add(file);
        partialScope.add(file);
      }
    }

    List<Finding> problems = new ArrayList<>();
    Map<Path, List<Suppression>> suppressionsByDir = new LinkedHashMap<>();
    for (Path file : files) {
      Path dir = file.resolveSibling(ELEMENT_TEMPLATES);
      List<Path> dirTemplates = templatesByDir.getOrDefault(dir, List.of());
      boolean fullScope = !partialScope.contains(file);
      SuppressionFile.Result loaded = SuppressionFile.load(file);
      problems.addAll(loaded.findings());
      List<Suppression> valid = new ArrayList<>();
      for (Suppression s : loaded.suppressions()) {
        Optional<Finding> problem =
            check(s, dir, dirTemplates, templates, suppressibleRules, fullScope);
        problem.ifPresentOrElse(problems::add, () -> valid.add(s));
      }
      suppressionsByDir.put(dir, valid);
    }

    List<Finding> remaining = new ArrayList<>();
    Set<Suppression> used = new HashSet<>();
    int suppressed = 0;
    for (Finding finding : findings) {
      List<Suppression> matches = matches(finding, templates, suppressionsByDir);
      if (!matches.isEmpty()) {
        used.addAll(matches);
        suppressed++;
      } else {
        remaining.add(finding);
      }
    }

    for (List<Suppression> list : suppressionsByDir.values()) {
      for (Suppression s : list) {
        if (!used.contains(s) && !partialScope.contains(s.file())) {
          problems.add(
              SuppressionFile.error(
                  s.file(), s.pointer(), "Suppression matches no finding. Remove it."));
        }
      }
    }
    remaining.addAll(problems);
    return new Result(remaining, suppressed);
  }

  private static Optional<Finding> check(
      Suppression s,
      Path dir,
      List<Path> dirTemplates,
      Map<Path, JsonNode> templates,
      Set<String> suppressibleRules,
      boolean fullScope) {
    if (!suppressibleRules.contains(s.rule())) {
      return Optional.of(
          SuppressionFile.error(
              s.file(),
              s.pointer() + "/" + SuppressionFile.RULE,
              "Unknown or non-suppressible rule \"" + s.rule() + "\"."));
    }
    if (!fullScope) {
      return Optional.empty();
    }
    List<Path> matched =
        dirTemplates.stream().filter(t -> s.templateMatcher().matches(dir.relativize(t))).toList();
    if (matched.isEmpty()) {
      return Optional.of(
          SuppressionFile.error(
              s.file(),
              s.pointer() + "/" + SuppressionFile.TEMPLATE,
              "\"" + s.template() + "\" matches no template under " + dir + "."));
    }
    if (s.property() != null
        && matched.stream().noneMatch(t -> hasProperty(templates.get(t), s.property()))) {
      return Optional.of(
          SuppressionFile.error(
              s.file(),
              s.pointer() + "/" + SuppressionFile.PROPERTY,
              "No template matching \""
                  + s.template()
                  + "\" has property \""
                  + s.property()
                  + "\"."));
    }
    return Optional.empty();
  }

  /** All entries covering the finding, so overlapping entries are all marked as used. */
  private static List<Suppression> matches(
      Finding finding, Map<Path, JsonNode> templates, Map<Path, List<Suppression>> byDir) {
    Optional<Path> dir = elementTemplatesDir(finding.file());
    if (dir.isEmpty()) {
      return List.of();
    }
    Path relative = dir.get().relativize(finding.file());
    String propertyId = propertyId(templates.get(finding.file()), finding.jsonPointer());
    return byDir.getOrDefault(dir.get(), List.of()).stream()
        .filter(s -> s.rule().equals(finding.ruleId()))
        .filter(s -> s.templateMatcher().matches(relative))
        .filter(s -> s.property() == null || s.property().equals(propertyId))
        .toList();
  }

  private static Optional<Path> elementTemplatesDir(Path file) {
    for (Path p = file.getParent(); p != null; p = p.getParent()) {
      Path name = p.getFileName();
      if (name != null && ELEMENT_TEMPLATES.equals(name.toString())) {
        return Optional.of(p);
      }
    }
    return Optional.empty();
  }

  /** Resolves {@code /properties/<i>/...} to {@code properties[i].id}, or {@code null}. */
  private static String propertyId(JsonNode template, String pointer) {
    if (template == null || pointer == null) {
      return null;
    }
    Matcher m = PROPERTY_POINTER.matcher(pointer);
    if (!m.matches()) {
      return null;
    }
    int index;
    try {
      index = Integer.parseInt(m.group(1));
    } catch (NumberFormatException e) {
      return null;
    }
    JsonNode id = template.path(ElementTemplate.PROPERTIES).path(index).path(ElementTemplate.ID);
    return id.isTextual() ? id.asText() : null;
  }

  private static boolean hasProperty(JsonNode template, String propertyId) {
    for (JsonNode p : template.path(ElementTemplate.PROPERTIES)) {
      if (propertyId.equals(p.path(ElementTemplate.ID).asText(null))) {
        return true;
      }
    }
    return false;
  }
}
