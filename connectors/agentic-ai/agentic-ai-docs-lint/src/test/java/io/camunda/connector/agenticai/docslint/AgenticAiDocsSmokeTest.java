/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.docslint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Proves that the config of {@link AgenticAiDocsTest} catches each rule: one defect per rule is
 * seeded in a small tree in the Agentic AI layout, and each is found through that config.
 */
class AgenticAiDocsSmokeTest {

  private static final String AGENTIC_AI = "connectors/agentic-ai";
  private static final String E2E = "connectors-e2e-test/connectors-e2e-test-agentic-ai";
  private static final String ALLOWED_NAMES_SOURCE =
      AGENTIC_AI
          + "/agentic-ai-docs-lint/src/test/java/io/camunda/connector/agenticai/docslint/AgenticAiDocsTest.java";

  private static final String DEFECTS =
      """
      # Doc

      [Gone](gone.md) and [Anchor](#nowhere)

      ```java
      ```

      `Missing` and `Agent#missing`
      """;

  @TempDir Path root;

  @BeforeEach
  void writeAValidTree() throws IOException {
    write(
        AGENTIC_AI + "/pom.xml",
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modules>
            <module>connector-aiagent</module>
            <module>aiagent-api</module>
          </modules>
        </project>
        """);
    write(
        AGENTIC_AI + "/aiagent-api/src/main/java/io/camunda/smoke/Agent.java",
        "package io.camunda.smoke; public class Agent { void run() {} }");
    write(AGENTIC_AI + "/connector-aiagent/README.md", readme("# AI Agent Task"));
    write(AGENTIC_AI + "/aiagent-api/README.md", readme("# aiagent-api"));
    write(E2E + "/README.md", readme("# connectors-e2e-test-agentic-ai"));
  }

  @Test
  void theValidTreeHasNoFindings() {
    final DocsLint lint = lint();

    assertThat(lint.docsInScope().stream().map(lint::findStaleReferences))
        .allSatisfy(findings -> assertThat(findings.all()).isEmpty());
    assertThat(lint.modulesInScope().stream().map(lint::findReadmeLayoutViolations))
        .allSatisfy(findings -> assertThat(findings.all()).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        AGENTIC_AI + "/aiagent-api/docs/doc.md",
        AGENTIC_AI + "/docs/architecture/doc.md",
        E2E + "/docs/doc.md"
      })
  void staleReferencesAreFoundInEveryDocRoot(final String path) throws IOException {
    write(path, DEFECTS);
    final String dir = Path.of(path).getParent().toString();

    final DocsLint lint = lint();

    assertThat(lint.docsInScope()).contains(Path.of(path));
    assertThat(heads(lint.findStaleReferences(Path.of(path))))
        .containsExactly(
            path
                + ":3: link `gone.md` points to a file that does not exist (resolved to `"
                + dir
                + "/gone.md`)",
            path + ":3: link `#nowhere` points to an anchor that does not exist in `" + path + "`",
            path + ":5: Java code block",
            path + ":8: unknown type `Missing` in `Missing`",
            path + ":8: no such member `missing` on `io.camunda.smoke.Agent`");
  }

  @Test
  void excludedDocsAreOutOfScopeAndTheBreakingChangesAreCheckedForLinksOnly() throws IOException {
    write(AGENTIC_AI + "/docs/adr/doc.md", DEFECTS);
    write(AGENTIC_AI + "/examples/doc.md", DEFECTS);
    write(AGENTIC_AI + "/aiagent-api/src/main/resources/doc.md", DEFECTS);
    final String breakingChanges = AGENTIC_AI + "/docs/breaking-changes.md";
    write(breakingChanges, "# Breaking changes\n\n[Gone](gone.md)\n\n`Missing`\n");

    final DocsLint lint = lint();

    assertThat(lint.docsInScope())
        .containsExactly(
            Path.of(E2E + "/README.md"),
            Path.of(AGENTIC_AI + "/aiagent-api/README.md"),
            Path.of(AGENTIC_AI + "/connector-aiagent/README.md"),
            Path.of(breakingChanges));
    assertThat(heads(lint.findStaleReferences(Path.of(breakingChanges))))
        .containsExactly(
            breakingChanges
                + ":3: link `gone.md` points to a file that does not exist (resolved to `"
                + AGENTIC_AI
                + "/docs/gone.md`)");
  }

  @Test
  void theModulesAreThoseOfTheModulesPomAndTheE2eModule() throws IOException {
    Files.delete(root.resolve(AGENTIC_AI + "/aiagent-api/README.md"));
    Files.delete(root.resolve(E2E + "/README.md"));

    final DocsLint lint = lint();

    assertThat(lint.modulesInScope())
        .containsExactly(
            new DocModule("connector-aiagent", Path.of(AGENTIC_AI + "/connector-aiagent")),
            new DocModule("aiagent-api", Path.of(AGENTIC_AI + "/aiagent-api")),
            new DocModule("connectors-e2e-test-agentic-ai", Path.of(E2E)));
    assertThat(lint.modulesInScope().stream().map(lint::findReadmeLayoutViolations))
        .map(AgenticAiDocsSmokeTest::heads)
        .containsExactly(
            List.of(),
            List.of(AGENTIC_AI + "/aiagent-api/README.md: README is missing"),
            List.of(E2E + "/README.md: README is missing"));
  }

  @ParameterizedTest
  @ValueSource(strings = {AGENTIC_AI + "/aiagent-api", E2E})
  void aWrongReadmeLayoutIsReported(final String dir) throws IOException {
    write(dir + "/README.md", "# wrong\n\n## Purpose\n\n## Docs\n\n## Key types\n");
    write(dir + "/docs/doc.md", "# Doc\n");
    final String readme = dir + "/README.md";

    assertThat(heads(lint().findReadmeLayoutViolations(module(dir))))
        .containsExactly(
            readme + ":1: first heading is `# wrong`, expected `# " + module(dir).name() + "`",
            readme
                + ":3: the first sections are [## Purpose, ## Docs, ## Key types], expected"
                + " [## Purpose, ## Key types, ## Docs]",
            readme + ":5: `## Docs` does not link `" + dir + "/docs/doc.md`");
  }

  @Test
  void theAiAgentReadmeIsHeldToItsTitle() throws IOException {
    final String readme = AGENTIC_AI + "/connector-aiagent/README.md";
    final DocModule module = module(AGENTIC_AI + "/connector-aiagent");

    assertThat(lint().findReadmeLayoutViolations(module).all()).isEmpty();

    write(readme, readme("# connector-aiagent"));

    assertThat(heads(lint().findReadmeLayoutViolations(module)))
        .containsExactly(
            readme + ":1: first heading is `# connector-aiagent`, expected `# AI Agent Task`");
  }

  @Test
  void staleAllowedNamesAreReported() throws IOException {
    write(AGENTIC_AI + "/docs/architecture/doc.md", "# Doc\n\n`Foo`\n");
    write(
        AGENTIC_AI + "/aiagent-api/src/main/java/io/camunda/smoke/Foo.java",
        "package io.camunda.smoke; public class Foo {}");

    assertThat(heads(lint().findStaleAllowedNames()))
        .contains(
            ALLOWED_NAMES_SOURCE + ": allowed name `Foo` is now declared by the code",
            ALLOWED_NAMES_SOURCE + ": allowed name `Read` is used in no doc");
  }

  private DocsLint lint() {
    return DocsLint.of(AgenticAiDocsTest.config(root));
  }

  private static DocModule module(final String dir) {
    return new DocModule(Path.of(dir).getFileName().toString(), Path.of(dir));
  }

  private static String readme(final String title) {
    return title + "\n\n## Purpose\n\n## Key types\n\n## Docs\n";
  }

  /** The position and the what of each finding, as the first line of the finding. */
  private static List<String> heads(final Findings findings) {
    return findings.all().stream()
        .map(finding -> finding.toString().lines().findFirst().orElseThrow())
        .toList();
  }

  private void write(final String path, final String content) throws IOException {
    final Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
