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
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocTreeTest {

  @TempDir Path root;

  @BeforeEach
  void createTree() throws IOException {
    write(
        "connectors/agentic-ai/pom.xml",
        "<project><modules><module>one</module><module>two</module></modules></project>");
    write("connectors/agentic-ai/README.md", "# root");
    write("connectors/agentic-ai/one/README.md", "# one");
    write("connectors/agentic-ai/one/Sample.java", "package p; class Sample {}");
    write("connectors/agentic-ai/docs/breaking-changes.md", "# history");
    write("connectors/agentic-ai/docs/adr/001.md", "# adr");
    write("connectors/agentic-ai/examples/e.md", "# example");
    write("connectors/agentic-ai/one/src/main/resources/prompt.md", "# prompt");
    write("connectors/agentic-ai/one/target/generated.md", "# build");
    write("connectors/agentic-ai/.hidden/h.md", "# hidden");
    write("connectors/agentic-ai/node_modules/n.md", "# node");
    write("connectors-e2e-test/connectors-e2e-test-agentic-ai/README.md", "# e2e");
    write("connectors/other/README.md", "# other");
  }

  @Test
  void listsTheDocsInScopeSortedAndWithoutExcludedDirectories() {
    assertThat(agenticAiTree(root).docPaths())
        .extracting(Path::toString)
        .containsExactly(
            "connectors-e2e-test/connectors-e2e-test-agentic-ai/README.md",
            "connectors/agentic-ai/README.md",
            "connectors/agentic-ai/docs/breaking-changes.md",
            "connectors/agentic-ai/one/README.md");
  }

  @Test
  void doesNotFollowSymbolicLinks() throws IOException {
    final Path linked = root.resolve("connectors/agentic-ai/linked.md");
    Files.createSymbolicLink(linked, root.resolve("connectors/agentic-ai/README.md"));

    assertThat(agenticAiTree(root).docPaths())
        .extracting(Path::toString)
        .containsExactly(
            "connectors-e2e-test/connectors-e2e-test-agentic-ai/README.md",
            "connectors/agentic-ai/README.md",
            "connectors/agentic-ai/docs/breaking-changes.md",
            "connectors/agentic-ai/one/README.md");
  }

  @Test
  void marksHistoryDocs() {
    final DocTree tree = agenticAiTree(root);

    assertThat(tree.isHistory(Path.of("connectors/agentic-ai/docs/breaking-changes.md"))).isTrue();
    assertThat(tree.isHistory(Path.of("connectors/agentic-ai/README.md"))).isFalse();
  }

  @Test
  void readsTheModulesFromThePomAndAddsTheE2eModule() {
    assertThat(agenticAiTree(root).modules())
        .containsExactly(
            new DocModule("one", Path.of("connectors/agentic-ai/one")),
            new DocModule("two", Path.of("connectors/agentic-ai/two")),
            new DocModule(
                "connectors-e2e-test-agentic-ai",
                Path.of("connectors-e2e-test/connectors-e2e-test-agentic-ai")));
  }

  @Test
  void readsOnlyTheDirectModulesOfThePom() throws IOException {
    write(
        "connectors/agentic-ai/pom.xml",
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modules><module>one</module><module>two</module></modules>
          <profiles>
            <profile>
              <modules><module>profiled</module></modules>
            </profile>
          </profiles>
        </project>
        """);

    assertThat(agenticAiTree(root).modules())
        .containsExactly(
            new DocModule("one", Path.of("connectors/agentic-ai/one")),
            new DocModule("two", Path.of("connectors/agentic-ai/two")),
            new DocModule(
                "connectors-e2e-test-agentic-ai",
                Path.of("connectors-e2e-test/connectors-e2e-test-agentic-ai")));
  }

  @Test
  void parsesADocOnceAndReturnsEmptyForAMissingFile() {
    final DocTree tree = agenticAiTree(root);
    final Path readme = Path.of("connectors/agentic-ai/one/README.md");

    assertThat(tree.doc(readme)).containsSame(tree.doc(readme).orElseThrow());
    assertThat(tree.doc(Path.of("connectors/agentic-ai/missing.md"))).isEmpty();
    assertThat(tree.exists(readme)).isTrue();
    assertThat(tree.exists(Path.of("connectors/agentic-ai/missing.md"))).isFalse();
  }

  @Test
  void readsTheJavaSourcesOfTheWholeRepositoryByPath() {
    assertThat(agenticAiTree(root).javaSources())
        .isEqualTo(Map.of("connectors/agentic-ai/one/Sample.java", "package p; class Sample {}"));
  }

  @Test
  void listsTheFilesBelowADirectory() {
    assertThat(agenticAiTree(root).files(Path.of("connectors/agentic-ai/one"), ".md"))
        .containsExactly(
            Path.of("connectors/agentic-ai/one/README.md"),
            Path.of("connectors/agentic-ai/one/src/main/resources/prompt.md"));
  }

  static DocTree agenticAiTree(final Path root) {
    return new DocTree(
        new DocsLintConfig(
            root,
            List.of(
                Path.of("connectors/agentic-ai"),
                Path.of("connectors-e2e-test/connectors-e2e-test-agentic-ai")),
            List.of(
                "connectors/agentic-ai/docs/adr/**",
                "connectors/agentic-ai/examples/**",
                "**/src/main/resources/**"),
            Set.of(Path.of("connectors/agentic-ai/docs/breaking-changes.md")),
            Path.of("connectors/agentic-ai/pom.xml"),
            List.of(Path.of("connectors-e2e-test/connectors-e2e-test-agentic-ai")),
            List.of(),
            Map.of(),
            Map.of(),
            Path.of("Test.java")));
  }

  private void write(final String path, final String content) throws IOException {
    final Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
