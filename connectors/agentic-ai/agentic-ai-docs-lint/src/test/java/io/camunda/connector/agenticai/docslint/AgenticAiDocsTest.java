/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.docslint;

import static org.assertj.core.api.Assertions.fail;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Keeps the Agentic AI docs in step with the modules and the code: every module has a README with
 * the fixed headings that links the module docs, every relative link resolves, the docs hold no
 * Java excerpts, and the types and members they name exist.
 */
class AgenticAiDocsTest {

  private static final Path AGENTIC_AI = Path.of("connectors/agentic-ai");
  private static final Path E2E = Path.of("connectors-e2e-test/connectors-e2e-test-agentic-ai");

  private static final DocsLint LINT = DocsLint.of(config(repositoryRoot()));

  @ParameterizedTest(name = "{0}")
  @MethodSource("docs")
  void docIsInStepWithTheCode(final Path path, final TestReporter reporter) {
    assertNoViolations(LINT.findStaleReferences(path), reporter);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("modules")
  void moduleReadmeFollowsTheLayout(final DocModule module, final TestReporter reporter) {
    assertNoViolations(LINT.findReadmeLayoutViolations(module), reporter);
  }

  static List<Path> docs() {
    return LINT.docsInScope();
  }

  static List<DocModule> modules() {
    return LINT.modulesInScope();
  }

  /** The config of the Agentic AI docs below the root of a repository. */
  static DocsLintConfig config(final Path root) {
    return DocsLintConfigBuilder.builder()
        .root(root)
        .docRoots(List.of(AGENTIC_AI, E2E))
        .excludedDocs(
            List.of(
                AGENTIC_AI + "/docs/adr/**",
                AGENTIC_AI + "/examples/**",
                "**/src/main/resources/**"))
        .modulesPom(AGENTIC_AI.resolve("pom.xml"))
        .extraModules(List.of(E2E))
        .readmeSections(List.of("## Purpose", "## Key types", "## Docs"))
        .readmeTitles(Map.of("connector-aiagent", "# AI Agent Task"))
        .build();
  }

  /** The root of the repository that holds this module, which is run from its directory. */
  private static Path repositoryRoot() {
    final Path moduleDir = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
    return moduleDir.getParent().getParent().getParent();
  }

  private static void assertNoViolations(final Findings findings, final TestReporter reporter) {
    findings
        .warnings()
        .forEach(warning -> {
          System.out.println(warning.logLine());
          reporter.publishEntry("warning", warning.toString());
        });
    findings.failureMessage().ifPresent(message -> fail(message));
  }
}
