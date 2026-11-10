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
import java.util.Set;
import org.junit.jupiter.api.Test;
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

  private static final Map<String, String> ALLOWED_NAMES =
      Map.ofEntries(
          Map.entry("Foo", "placeholder in the naming rule `FooImpl` implements `Foo`"),
          Map.entry("FooImpl", "placeholder in the naming rule"),
          Map.entry("Impl", "suffix of the naming rule"),
          Map.entry("Interface", "placeholder in the naming rule `<Interface>Impl`"),
          Map.entry("MyFilesystem", "example gateway tool name"),
          Map.entry("Read", "the Read tool of the coding agent, not a type"),
          Map.entry("Authorization", "HTTP header name"),
          Map.entry("CreateEvent", "AWS AgentCore API operation"),
          Map.entry("ListEvents", "AWS AgentCore API operation"),
          Map.entry("Inject", "annotation of an injection library, named in a rule"),
          Map.entry("CompleteJobCommand", "Zeebe client command, not a type of this repo"),
          Map.entry("MarshallingType", "AWS SDK type"),
          Map.entry("Credential", "openai-java SDK type"),
          Map.entry("ManagedIdentityCredential", "Azure SDK type"),
          Map.entry("CachedContent", "Gemini SDK type"),
          Map.entry("RetryInterceptor", "Google GenAI SDK type"),
          Map.entry("ThinkChunk", "Mistral schema name"));

  private static final DocsLint LINT =
      DocsLint.of(
          new DocsLintConfig(
              repositoryRoot(),
              List.of(AGENTIC_AI, E2E),
              List.of(
                  AGENTIC_AI + "/docs/adr/**",
                  AGENTIC_AI + "/examples/**",
                  "**/src/main/resources/**"),
              Set.of(AGENTIC_AI.resolve("docs/breaking-changes.md")),
              AGENTIC_AI.resolve("pom.xml"),
              List.of(E2E),
              List.of("## Purpose", "## Key types", "## Docs"),
              Map.of("connector-aiagent", "# AI Agent Task"),
              ALLOWED_NAMES,
              AGENTIC_AI.resolve(
                  "agentic-ai-docs-lint/src/test/java/io/camunda/connector/agenticai/docslint/AgenticAiDocsTest.java")));

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

  @Test
  void allowedNamesAreUsedAndUnknownToTheCode(final TestReporter reporter) {
    assertNoViolations(LINT.findStaleAllowedNames(), reporter);
  }

  static List<Path> docs() {
    return LINT.docsInScope();
  }

  static List<DocModule> modules() {
    return LINT.modulesInScope();
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
