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
package io.camunda.connector.validator.command;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.validator.core.Finding;
import io.camunda.connector.validator.core.MultiFileRule;
import io.camunda.connector.validator.core.ReportPrinter;
import io.camunda.connector.validator.core.Rule;
import io.camunda.connector.validator.core.Severity;
import io.camunda.connector.validator.core.TemplateFinder;
import io.camunda.connector.validator.core.TemplateLoader;
import io.camunda.connector.validator.rule.ConditionPropertyOrderRule;
import io.camunda.connector.validator.rule.ConditionTargetExistsRule;
import io.camunda.connector.validator.rule.ConditionValueInChoicesRule;
import io.camunda.connector.validator.rule.CurrentVersionBumpRule;
import io.camunda.connector.validator.rule.DefaultValueInChoicesRule;
import io.camunda.connector.validator.rule.EmptyGroupRule;
import io.camunda.connector.validator.rule.GroupTargetExistsRule;
import io.camunda.connector.validator.rule.HybridParityRule;
import io.camunda.connector.validator.rule.PresetConditionsSatisfiedRule;
import io.camunda.connector.validator.rule.PresetCoverageRule;
import io.camunda.connector.validator.rule.PresetIdResolvesRule;
import io.camunda.connector.validator.rule.PresetIdUniqueRule;
import io.camunda.connector.validator.rule.PresetOperationGroupConsistencyRule;
import io.camunda.connector.validator.rule.PresetTargetExistsRule;
import io.camunda.connector.validator.rule.SchemaRule;
import io.camunda.connector.validator.rule.StepGroupShapeRule;
import io.camunda.connector.validator.rule.StepLeafShapeRule;
import io.camunda.connector.validator.rule.StepsPresetsPresentRule;
import io.camunda.connector.validator.rule.TaskDefinitionBindingFormRule;
import io.camunda.connector.validator.rule.UniqueGroupIdRule;
import io.camunda.connector.validator.rule.UniqueIdVersionRule;
import io.camunda.connector.validator.rule.UniquePropertyIdRule;
import io.camunda.connector.validator.rule.VersionedTemplateConsistencyRule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
    name = "element-template-validator",
    description = "Validates Camunda element templates against schema and semantic rules.",
    mixinStandardHelpOptions = true)
public class ValidatorCommand implements Callable<Integer> {

  @CommandLine.Option(
      names = {"-d", "--directory"},
      description =
          "Root directory to scan for element-templates/*.json files. Defaults to ./connectors.")
  private Path root = Path.of("connectors");

  @CommandLine.Option(
      names = "--skip-directory",
      split = ",",
      description =
          "Directory names pruned during template discovery (by exact name, not path). "
              + "Repeatable; comma-separated values are also accepted. "
              + "When specified, replaces the default set entirely. "
              + "Default: target, node_modules, .git, .idea, .m2.")
  private Set<String> skippedDirectories =
      new LinkedHashSet<>(TemplateFinder.DEFAULT_SKIPPED_DIRECTORIES);

  @CommandLine.Option(
      names = "--skip-connector",
      split = ",",
      description =
          "Connector directory names whose templates are skipped entirely (by exact name, not path). "
              + "Repeatable; comma-separated values are also accepted. "
              + "When specified, replaces the default set entirely. "
              + "Default: agentic-ai.")
  private Set<String> skippedConnectors =
      new LinkedHashSet<>(TemplateFinder.DEFAULT_SKIPPED_CONNECTORS);

  @CommandLine.Option(
      names = "--no-color",
      description =
          "Disable ANSI color in the output. Color is auto-disabled when no console is attached "
              + "(e.g. when output is redirected to a file or CI log capture).")
  private boolean noColor;

  @CommandLine.Option(
      names = "--schema-url",
      description =
          "Override the JSON-schema URL used by the schema rule. If unset, falls back to the "
              + "CAMUNDA_TEMPLATE_SCHEMA_URL environment variable, then to the pinned default "
              + "(zeebe-element-templates-json-schema@"
              + SchemaRule.SCHEMA_VERSION
              + ").")
  private String schemaUrl;

  @Override
  public Integer call() {
    List<Path> all = TemplateFinder.findAll(root, skippedDirectories, skippedConnectors);
    if (all.isEmpty()) {
      System.out.printf("No element-templates/*.json files found under %s%n", root);
      return 0;
    }

    LoadResult loaded = loadTemplates(all);
    List<Finding> findings = new ArrayList<>(loaded.parseFindings());

    Map<Path, JsonNode> templates = loaded.templates();

    SchemaRule schemaRule = new SchemaRule(resolveSchemaUrl());
    findings.addAll(runSingleFileRules(templates, schemaRule));
    findings.addAll(runMultiFileRules(templates));

    boolean colorEnabled = !noColor && System.console() != null;
    ReportPrinter.print(findings, templates.size(), System.out, colorEnabled);
    return findings.stream().anyMatch(f -> f.severity() == Severity.ERROR) ? 1 : 0;
  }

  private String resolveSchemaUrl() {
    if (schemaUrl != null && !schemaUrl.isBlank()) {
      return schemaUrl;
    }
    String fromEnv = System.getenv("CAMUNDA_TEMPLATE_SCHEMA_URL");
    if (fromEnv != null && !fromEnv.isBlank()) {
      return fromEnv;
    }
    return SchemaRule.SCHEMA_URL;
  }

  private static LoadResult loadTemplates(List<Path> all) {
    Map<Path, JsonNode> templates = new LinkedHashMap<>();
    List<Finding> parseFindings = new ArrayList<>();
    for (Path path : all) {
      TemplateLoader.Result result = TemplateLoader.load(path);
      if (result.ok()) {
        templates.put(path, result.node());
      } else {
        parseFindings.add(result.finding());
      }
    }
    return new LoadResult(templates, parseFindings);
  }

  private static List<Finding> runSingleFileRules(Map<Path, JsonNode> templates, Rule schema) {
    List<Rule> rules =
        List.of(
            new PresetTargetExistsRule(),
            new ConditionTargetExistsRule(),
            new ConditionValueInChoicesRule(),
            new ConditionPropertyOrderRule(),
            new GroupTargetExistsRule(),
            new DefaultValueInChoicesRule(),
            new UniqueGroupIdRule(),
            new UniquePropertyIdRule(),
            new EmptyGroupRule(),
            new TaskDefinitionBindingFormRule(),
            new StepsPresetsPresentRule(),
            new StepGroupShapeRule(),
            new StepLeafShapeRule(),
            new PresetIdUniqueRule(),
            new PresetIdResolvesRule(),
            new PresetOperationGroupConsistencyRule(),
            new PresetConditionsSatisfiedRule(),
            new PresetCoverageRule());
    List<Finding> findings = new ArrayList<>();
    for (Map.Entry<Path, JsonNode> entry : templates.entrySet()) {
      Path path = entry.getKey();
      if (TemplateFinder.isVersioned(path)) {
        continue;
      }
      JsonNode tree = entry.getValue();
      // Schema rule runs first; if it produces findings, skip semantic rules for that file
      // to avoid cascades of noisy follow-on errors.
      List<Finding> schemaFindings = schema.apply(path, tree);
      findings.addAll(schemaFindings);
      if (!schemaFindings.isEmpty()) {
        continue;
      }
      for (Rule rule : rules) {
        findings.addAll(rule.apply(path, tree));
      }
    }
    return findings;
  }

  private static List<Finding> runMultiFileRules(Map<Path, JsonNode> templates) {
    List<MultiFileRule> multiFileRules =
        List.of(
            new HybridParityRule(),
            new VersionedTemplateConsistencyRule(),
            new CurrentVersionBumpRule(),
            new UniqueIdVersionRule());
    List<Finding> findings = new ArrayList<>();
    for (MultiFileRule rule : multiFileRules) {
      findings.addAll(rule.apply(templates));
    }
    return findings;
  }

  private record LoadResult(Map<Path, JsonNode> templates, List<Finding> parseFindings) {}
}
