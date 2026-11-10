/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.docslint;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A parsed Markdown document. The path is relative to the repository root. Headings are given as
 * written in ATX form, for example {@code ## Docs}. Links are the relative destinations of the
 * links in the text, and definitions are those of the reference definitions. Fences hold the
 * language of each fenced code block.
 */
record Doc(
    Path path,
    List<Ref> headings,
    List<Ref> links,
    List<Ref> definitions,
    List<CodeSpan> codeSpans,
    List<Ref> fences,
    Set<String> anchors) {

  Doc {
    headings = List.copyOf(headings);
    links = List.copyOf(links);
    definitions = List.copyOf(definitions);
    codeSpans = List.copyOf(codeSpans);
    fences = List.copyOf(fences);
    anchors = Collections.unmodifiableSet(new LinkedHashSet<>(anchors));
  }

  /** The fenced code blocks of the doc must not be Java code. */
  Stream<Finding> javaFences() {
    return fences.stream()
        .filter(fence -> fence.text().equalsIgnoreCase("java"))
        .map(fence ->
            new Violation(
                path,
                fence.line(),
                "Java code block",
                "a code excerpt goes stale when the code changes",
                "replace the excerpt with the name of the type and member, for example"
                    + " `Type#member`"));
  }

  /** The first heading must be the title. */
  Optional<Violation> firstHeading(final String title) {
    if (!headings.isEmpty() && headings.getFirst().text().equals(title)) {
      return Optional.empty();
    }
    return Optional.of(
        new Violation(
            path,
            headings.isEmpty() ? 1 : headings.getFirst().line(),
            "first heading is "
                + (headings.isEmpty() ? "missing" : "`" + headings.getFirst().text() + "`")
                + ", expected `"
                + title
                + "`",
            "the first heading names the module, so a reader and a search find the README by it",
            "start the README with `" + title + "`"));
  }

  /** The first {@code ##} sections must be the expected ones, in order. */
  Optional<Violation> sections(final List<String> expected) {
    final List<Ref> sections =
        headings.stream()
            .filter(heading -> heading.text().startsWith("## "))
            .limit(expected.size())
            .toList();
    final List<String> actual = sections.stream().map(Ref::text).toList();
    if (actual.equals(expected)) {
      return Optional.empty();
    }
    return Optional.of(
        new Violation(
            path,
            sections.isEmpty() ? 1 : sections.getFirst().line(),
            "the first sections are " + actual + ", expected " + expected,
            "all module READMEs share one layout, so a reader finds the same section in each",
            "put the sections in this order before any other `##` section"));
  }

  /** The links of the {@code ## Docs} section. Empty without the section. */
  List<Ref> docsLinks() {
    final Optional<Ref> docs = docsHeading();
    final int end =
        docs.flatMap(section ->
                headings.stream()
                    .filter(h -> h.line() > section.line() && h.text().startsWith("## "))
                    .findFirst())
            .map(Ref::line)
            .orElse(Integer.MAX_VALUE);
    return links.stream()
        .filter(link -> docs.isPresent() && link.line() > docs.get().line() && link.line() < end)
        .toList();
  }

  /** The line of the {@code ## Docs} heading, or 1 without the section. */
  int docsLine() {
    return docsHeading().map(Ref::line).orElse(1);
  }

  private Optional<Ref> docsHeading() {
    return headings.stream().filter(heading -> heading.text().equals("## Docs")).findFirst();
  }
}
