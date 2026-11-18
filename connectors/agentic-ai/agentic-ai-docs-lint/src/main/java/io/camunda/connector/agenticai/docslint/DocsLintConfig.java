/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.docslint;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * What {@link DocsLint} checks. All paths except the root are relative to the root.
 *
 * <p>A module README must start with its title and the README sections, and must link every
 * Markdown file of the {@code docs} directory of the module under its {@code ## Docs} section.
 *
 * @param root the repository root. The Java sources below it are the code the docs are checked
 *     against.
 * @param docRoots the directories whose Markdown files are the docs
 * @param excludedDocs glob patterns of the docs that are not checked
 * @param modulesPom the pom whose modules are checked. A module directory is relative to it.
 * @param extraModules the directories of further modules, named after the directory
 * @param readmeSections the {@code ##} headings each README starts with, in order
 * @param readmeTitles the first heading of the README by module name, when it is not {@code #
 *     <module name>}
 */
@RecordBuilder
@RecordBuilder.Options(
    publicBuilderConstructors = true,
    interpretNotNulls = true,
    defaultNotNull = true)
public record DocsLintConfig(
    Path root,
    List<Path> docRoots,
    List<String> excludedDocs,
    Path modulesPom,
    List<Path> extraModules,
    List<String> readmeSections,
    Map<String, String> readmeTitles) {

  public DocsLintConfig {
    docRoots = List.copyOf(docRoots);
    excludedDocs = List.copyOf(excludedDocs);
    extraModules = List.copyOf(extraModules);
    readmeSections = List.copyOf(readmeSections);
    readmeTitles = Map.copyOf(readmeTitles);
  }
}
