/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.docslint;

import static org.assertj.core.api.Assertions.fail;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Keeps the Agentic AI docs in step with the modules and the code: every module has a README with
 * the fixed headings that links the module docs, every relative link resolves, the docs hold no
 * Java excerpts, the types and members they name exist, and every public type of the {@code
 * aiagent-api} lock is named.
 */
class ReferenceDocsTest {

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

  private static final Path ALLOWED_NAMES_SOURCE =
      DocTree.AGENTIC_AI.resolve(
          "agentic-ai-docs-lint/src/test/java/io/camunda/connector/agenticai/docslint/ReferenceDocsTest.java");

  private static final Path API_LOCK =
      DocTree.AGENTIC_AI.resolve("aiagent-api/docs/api-signatures.lock");

  private static final DocTree TREE = DocTree.ofRepository();
  private static final JavaSourceIndex JAVA_SOURCES = JavaSourceIndex.of(TREE.javaSources());

  @ParameterizedTest(name = "{0}")
  @MethodSource("docs")
  void relativeLinksResolve(final Path path, final TestReporter reporter) {
    assertNoViolations(DocChecks.links(doc(path), TREE), reporter);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("docs")
  void noJavaCodeBlocks(final Path path, final TestReporter reporter) {
    assertNoViolations(doc(path).javaFences(), reporter);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("currentDocs")
  void namedTypesExist(final Path path, final TestReporter reporter) {
    assertNoViolations(DocChecks.types(doc(path), JAVA_SOURCES, ALLOWED_NAMES.keySet()), reporter);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("currentDocs")
  void memberReferencesResolve(final Path path, final TestReporter reporter) {
    assertNoViolations(DocChecks.members(doc(path), JAVA_SOURCES), reporter);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("modules")
  void moduleReadmeFollowsTheLayout(final DocModule module, final TestReporter reporter) {
    assertNoViolations(DocChecks.readme(module, TREE), reporter);
  }

  @Test
  void allowedNamesAreUsedAndUnknownToTheCode(final TestReporter reporter) {
    assertNoViolations(
        DocChecks.allowedNames(
            ALLOWED_NAMES,
            DocChecks.namedTypes(currentDocs().map(ReferenceDocsTest::doc)),
            JAVA_SOURCES,
            ALLOWED_NAMES_SOURCE),
        reporter);
  }

  @Test
  void everyPublicApiTypeIsNamedInTheDocs(final TestReporter reporter) {
    assertNoViolations(
        DocChecks.apiTypesNamed(
            TREE.lines(API_LOCK),
            API_LOCK,
            DocChecks.namedTypes(currentDocs().map(ReferenceDocsTest::doc))),
        reporter);
  }

  static Stream<Path> docs() {
    return TREE.docPaths().stream();
  }

  static Stream<Path> currentDocs() {
    return docs().filter(path -> !TREE.isHistory(path));
  }

  static List<DocModule> modules() {
    return TREE.modules();
  }

  private static Doc doc(final Path path) {
    return TREE.doc(path).orElseThrow();
  }

  private static void assertNoViolations(
      final Stream<Finding> findings, final TestReporter reporter) {
    final List<String> violations = new ArrayList<>();
    findings.forEach(finding -> {
      switch (finding) {
        case final Warning warning -> {
          System.out.println(warning.logLine());
          reporter.publishEntry("warning", warning.toString());
        }
        case final Violation violation -> violations.add(violation.toString());
      }
    });
    if (!violations.isEmpty()) {
      fail("\n" + String.join("\n", violations));
    }
  }
}
