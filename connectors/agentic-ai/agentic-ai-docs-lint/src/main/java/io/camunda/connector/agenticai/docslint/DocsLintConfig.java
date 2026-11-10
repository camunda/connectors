/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.docslint;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * @param historyDocs the docs that record history and may name types that no longer exist
 * @param modulesPom the pom whose modules are checked. A module directory is relative to it.
 * @param extraModules the directories of further modules, named after the directory
 * @param readmeSections the {@code ##} headings each README starts with, in order
 * @param readmeTitles the first heading of the README by module name, when it is not {@code #
 *     <module name>}
 * @param allowedNames the names that the docs may use although the code does not know them, each
 *     with the reason
 * @param allowedNamesSource the file that holds the allowed names, given as the file of a finding
 *     about them
 */
public record DocsLintConfig(
    Path root,
    List<Path> docRoots,
    List<String> excludedDocs,
    Set<Path> historyDocs,
    Path modulesPom,
    List<Path> extraModules,
    List<String> readmeSections,
    Map<String, String> readmeTitles,
    Map<String, String> allowedNames,
    Path allowedNamesSource) {

  public DocsLintConfig {
    docRoots = List.copyOf(docRoots);
    excludedDocs = List.copyOf(excludedDocs);
    historyDocs = Set.copyOf(historyDocs);
    extraModules = List.copyOf(extraModules);
    readmeSections = List.copyOf(readmeSections);
    readmeTitles = Map.copyOf(readmeTitles);
    allowedNames = Map.copyOf(allowedNames);
  }
}
