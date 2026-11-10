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
  public List<Path> docsInScope() {
    return tree.docPaths();
  }

  /** The modules of the modules pom, then the extra modules. */
  public List<DocModule> modulesInScope() {
    return tree.modules();
  }

  /**
   * The stale references of the doc: relative links and anchors that do not resolve, and Java code
   * blocks. Unless it is a history doc, also the types and members of its code spans that the code
   * does not know.
   */
  public Findings findStaleReferences(final Path doc) {
    final Doc parsed =
        tree.doc(doc).orElseThrow(() -> new IllegalArgumentException("No doc at " + doc));
    return new Findings(
        Stream.of(
                DocChecks.links(parsed, tree),
                DocChecks.javaFences(parsed),
                tree.currentDoc(doc).stream()
                    .flatMap(current ->
                        Stream.concat(
                            DocChecks.types(current, index(), config.allowedNames().keySet()),
                            DocChecks.members(current, index()))))
            .flatMap(Function.identity())
            .toList());
  }

  /**
   * The violations of the README layout: the README of the module is missing, does not start with
   * the module title and the README sections, or does not link every doc of the module under {@code
   * ## Docs}.
   */
  public Findings findReadmeLayoutViolations(final DocModule module) {
    return new Findings(
        DocChecks.readme(module, tree, config.readmeSections(), config.readmeTitles()).toList());
  }

  /**
   * The stale allowed names: those that no doc uses outside the history docs, and those that the
   * code now declares.
   */
  public Findings findStaleAllowedNames() {
    return new Findings(
        DocChecks.allowedNames(
                config.allowedNames(),
                DocChecks.namedTypes(tree.currentDocs()),
                index(),
                config.allowedNamesSource())
            .toList());
  }

  private JavaSourceIndex index() {
    if (index == null) {
      index = JavaSourceIndex.of(tree.javaSources());
    }
    return index;
  }
}
