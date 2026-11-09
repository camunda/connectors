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
import java.util.Set;
import java.util.stream.Stream;

/**
 * A parsed Markdown document. The path is relative to the repository root. Headings are given as
 * written in ATX form, for example {@code ## Docs}. Links are the relative destinations. Fences
 * hold the language of each fenced code block.
 */
record Doc(
    Path path,
    List<Ref> headings,
    List<Ref> links,
    List<Ref> codeSpans,
    List<Ref> fences,
    Set<String> anchors) {

  Doc {
    headings = List.copyOf(headings);
    links = List.copyOf(links);
    codeSpans = List.copyOf(codeSpans);
    fences = List.copyOf(fences);
    anchors = Collections.unmodifiableSet(new LinkedHashSet<>(anchors));
  }

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
}
