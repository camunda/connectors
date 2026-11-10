/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.docslint;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Keeps the docs of a repository in step with its modules and its code. The code is the source of
 * truth: a finding says what is wrong, why it matters and what to verify. The Java sources are read
 * once, on the first check that needs them.
 */
public final class DocsLint {

  private final DocsLintConfig config;
  private final DocTree tree;
  private JavaSourceIndex index;

  private DocsLint(final DocsLintConfig config) {
    this.config = config;
    this.tree = new DocTree(config);
  }

  public static DocsLint of(final DocsLintConfig config) {
    return new DocsLint(config);
  }

  /** The docs in scope, sorted. */
  public List<Path> docs() {
    return tree.docPaths();
  }

  /** The modules of the modules pom, then the extra modules. */
  public List<DocModule> modules() {
    return tree.modules();
  }

  /**
   * The relative links of the doc resolve and it holds no Java code block. Unless it is a history
   * doc, the types and members that it names in code spans exist in the code.
   */
  public Stream<Finding> checkDoc(final Path path) {
    final Doc doc =
        tree.doc(path).orElseThrow(() -> new IllegalArgumentException("No doc at " + path));
    final Stream<Finding> findings =
        Stream.concat(DocChecks.links(doc, tree), DocChecks.javaFences(doc));
    if (tree.isHistory(path)) {
      return findings;
    }
    return Stream.of(
            findings,
            DocChecks.types(doc, index(), config.allowedNames().keySet()),
            DocChecks.members(doc, index()))
        .flatMap(Function.identity());
  }

  /**
   * The README of the module exists, starts with the module title and the README sections, and
   * links every doc of the module under {@code ## Docs}.
   */
  public Stream<Finding> checkModule(final DocModule module) {
    return DocChecks.readme(module, tree, config.readmeSections(), config.readmeTitles());
  }

  /** Each allowed name is used in a doc that is no history doc and is unknown to the code. */
  public Stream<Finding> checkConfig() {
    return DocChecks.allowedNames(
        config.allowedNames(),
        DocChecks.namedTypes(
            docs().stream()
                .filter(path -> !tree.isHistory(path))
                .map(path -> tree.doc(path).orElseThrow())),
        index(),
        config.allowedNamesSource());
  }

  private JavaSourceIndex index() {
    if (index == null) {
      index = JavaSourceIndex.of(tree.javaSources());
    }
    return index;
  }
}
