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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SuppressionFilterTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Set<String> RULES = Set.of("text-rule", "other-rule");

  @TempDir Path module;
  private Path template;
  private Path hybrid;
  private final Map<Path, JsonNode> templates = new LinkedHashMap<>();

  @BeforeEach
  void setUp() throws Exception {
    template = module.resolve("element-templates/a.json");
    hybrid = module.resolve("element-templates/hybrid/a-hybrid.json");
    JsonNode tree =
        MAPPER.readTree("{ \"properties\": [ { \"id\": \"first\" }, { \"id\": \"second\" } ] }");
    templates.put(template, tree);
    templates.put(hybrid, tree);
  }

  @Test
  void noSuppressionFile_findingsUnchanged() {
    List<Finding> findings = List.of(finding(template, "/properties/0/description", "text-rule"));
    var result = apply(findings);
    assertThat(result.findings()).isEqualTo(findings);
    assertThat(result.suppressed()).isZero();
  }

  @Test
  void propertySuppression_matchesByIdNotIndex() throws Exception {
    write(
        """
        { "suppressions": [
          { "rule": "text-rule", "template": "a.json", "property": "second", "reason": "R." }
        ] }
        """);
    Finding onSecond = finding(template, "/properties/1/tooltip", "text-rule");
    Finding onFirst = finding(template, "/properties/0/tooltip", "text-rule");
    Finding otherRule = finding(template, "/properties/1/tooltip", "other-rule");
    Finding otherFile = finding(hybrid, "/properties/1/tooltip", "text-rule");

    var result = apply(List.of(onSecond, onFirst, otherRule, otherFile));

    assertThat(result.suppressed()).isEqualTo(1);
    assertThat(result.findings()).containsExactly(onFirst, otherRule, otherFile);
  }

  @Test
  void templateWideGlobSuppression_matchesSubdirectories() throws Exception {
    write(
        """
        { "suppressions": [ { "rule": "text-rule", "template": "**.json", "reason": "R." } ] }
        """);
    var result =
        apply(
            List.of(finding(template, "/groups/0", "text-rule"), finding(hybrid, "", "text-rule")));
    assertThat(result.findings()).isEmpty();
    assertThat(result.suppressed()).isEqualTo(2);
  }

  @Test
  void propertySuppression_matchesEmbeddedConfigurationTemplateProperty() throws Exception {
    templates.put(
        template,
        MAPPER.readTree(
            """
            { "properties": [ { "id": "first" } ],
              "configurationTemplates": [ { "properties": [ { "id": "x" }, { "id": "embedded" } ] } ] }
            """));
    write(
        """
        { "suppressions": [
          { "rule": "text-rule", "template": "a.json", "property": "embedded", "reason": "R." }
        ] }
        """);
    Finding onEmbedded =
        finding(template, "/configurationTemplates/0/properties/1/condition/property", "text-rule");
    Finding onOtherEmbedded =
        finding(template, "/configurationTemplates/0/properties/0/condition", "text-rule");

    var result = apply(List.of(onEmbedded, onOtherEmbedded));

    assertThat(result.suppressed()).isEqualTo(1);
    assertThat(result.findings()).containsExactly(onOtherEmbedded);
  }

  @Test
  void invalidOrUnusedSuppressions_reported() throws Exception {
    write(
        """
        { "suppressions": [
          { "rule": "schema", "template": "a.json", "reason": "Not suppressible." },
          { "rule": "text-rule", "template": "missing.json", "reason": "No template." },
          { "rule": "text-rule", "template": "a.json", "property": "nope", "reason": "No prop." },
          { "rule": "other-rule", "template": "a.json", "reason": "Stale." }
        ] }
        """);
    var result = apply(List.of());
    assertThat(result.findings())
        .allMatch(f -> f.ruleId().equals(SuppressionFile.RULE_ID))
        .extracting(Finding::jsonPointer)
        .containsExactly(
            "/suppressions/0/rule",
            "/suppressions/1/template",
            "/suppressions/2/property",
            "/suppressions/3");
  }

  @Test
  void overlappingSuppressions_allMarkedUsedInEitherOrder() throws Exception {
    String wide = "{ \"rule\": \"text-rule\", \"template\": \"a.json\", \"reason\": \"R.\" }";
    String narrow =
        "{ \"rule\": \"text-rule\", \"template\": \"a.json\", \"property\": \"second\","
            + " \"reason\": \"R.\" }";
    List<Finding> findings =
        List.of(
            finding(template, "/properties/0/tooltip", "text-rule"),
            finding(template, "/properties/1/tooltip", "text-rule"));

    for (String entries : List.of(wide + ", " + narrow, narrow + ", " + wide)) {
      write("{ \"suppressions\": [ " + entries + " ] }");
      var result = apply(findings);
      assertThat(result.findings()).isEmpty();
      assertThat(result.suppressed()).isEqualTo(2);
    }
  }

  @Test
  void suppressionFileInModuleWithoutTemplates_entriesReported() throws Exception {
    templates.clear();
    write(
        """
        { "suppressions": [ { "rule": "text-rule", "template": "a.json", "reason": "R." } ] }
        """);
    assertThat(apply(List.of()).findings())
        .extracting(Finding::jsonPointer)
        .containsExactly("/suppressions/0/template");
  }

  @Test
  void scanRootInsideModule_appliesModuleSuppressionsWithoutScopeChecks() throws Exception {
    templates.remove(template);
    write(
        """
        { "suppressions": [
          { "rule": "text-rule", "template": "hybrid/*.json", "property": "second", "reason": "R." },
          { "rule": "text-rule", "template": "a.json", "reason": "Out of scope." },
          { "rule": "unknown-rule", "template": "a.json", "reason": "Still invalid." }
        ] }
        """);
    Finding onHybrid = finding(hybrid, "/properties/1/tooltip", "text-rule");
    Path scanRoot = Files.createDirectories(module.resolve("element-templates/hybrid"));

    var result =
        SuppressionFilter.apply(
            List.of(onHybrid),
            templates,
            TemplateFinder.findSuppressionFiles(scanRoot, Set.of(), Set.of()),
            scanRoot,
            Set.of(),
            RULES);

    assertThat(result.suppressed()).isEqualTo(1);
    assertThat(result.findings())
        .extracting(Finding::jsonPointer)
        .containsExactly("/suppressions/2/rule");
  }

  @Test
  void emptyScanRootInsideModule_validatesModuleSuppressionsWithoutScopeChecks() throws Exception {
    templates.clear();
    write(
        """
        { "suppressions": [
          { "rule": "text-rule", "template": "a.json", "reason": "Out of scope." },
          { "rule": "unknown-rule", "template": "a.json", "reason": "Still invalid." }
        ] }
        """);
    Path scanRoot = Files.createDirectories(module.resolve("element-templates/hybrid"));

    var result =
        SuppressionFilter.apply(
            List.of(),
            templates,
            TemplateFinder.findSuppressionFiles(scanRoot, Set.of(), Set.of()),
            scanRoot,
            Set.of(),
            RULES);

    assertThat(result.findings())
        .extracting(Finding::jsonPointer)
        .containsExactly("/suppressions/1/rule");
  }

  @Test
  void skippedDirectoryInsideModule_appliesModuleSuppressionsWithoutScopeChecks() throws Exception {
    templates.remove(hybrid);
    Files.createDirectories(module.resolve("element-templates/hybrid"));
    write(
        """
        { "suppressions": [
          { "rule": "text-rule", "template": "a.json", "property": "second", "reason": "R." },
          { "rule": "text-rule", "template": "hybrid/*.json", "reason": "Skipped." },
          { "rule": "unknown-rule", "template": "a.json", "reason": "Still invalid." }
        ] }
        """);
    Finding onTemplate = finding(template, "/properties/1/tooltip", "text-rule");
    Set<String> skippedDirs = Set.of("hybrid");

    var result =
        SuppressionFilter.apply(
            List.of(onTemplate),
            templates,
            TemplateFinder.findSuppressionFiles(module, skippedDirs, Set.of()),
            module,
            skippedDirs,
            RULES);

    assertThat(result.suppressed()).isEqualTo(1);
    assertThat(result.findings())
        .extracting(Finding::jsonPointer)
        .containsExactly("/suppressions/2/rule");
  }

  @Test
  void skippedElementTemplatesDirectory_appliesModuleSuppressionsWithoutScopeChecks()
      throws Exception {
    templates.clear();
    Files.createDirectories(module.resolve("element-templates"));
    write(
        """
        { "suppressions": [
          { "rule": "text-rule", "template": "a.json", "property": "second", "reason": "R." },
          { "rule": "unknown-rule", "template": "a.json", "reason": "Still invalid." }
        ] }
        """);
    Set<String> skippedDirs = Set.of("element-templates");

    var result =
        SuppressionFilter.apply(
            List.of(),
            templates,
            TemplateFinder.findSuppressionFiles(module, skippedDirs, Set.of()),
            module,
            skippedDirs,
            RULES);

    assertThat(result.findings())
        .extracting(Finding::jsonPointer)
        .containsExactly("/suppressions/1/rule");
  }

  private SuppressionFilter.Result apply(List<Finding> findings) {
    return SuppressionFilter.apply(
        findings,
        templates,
        TemplateFinder.findSuppressionFiles(module, Set.of(), Set.of()),
        module,
        Set.of(),
        RULES);
  }

  private void write(String json) throws Exception {
    Files.writeString(module.resolve(SuppressionFile.FILE_NAME), json);
  }

  private static Finding finding(Path file, String pointer, String rule) {
    return Finding.error(file, pointer, rule, "message");
  }
}
