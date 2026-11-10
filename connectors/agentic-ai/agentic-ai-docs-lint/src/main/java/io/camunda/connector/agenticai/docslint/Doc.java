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
    List<Ref> codeSpans,
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
}
