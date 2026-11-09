/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.docslint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MarkdownParserTest {

  @Test
  void aLongerFenceKeepsAShorterFenceInside() {
    final Doc doc =
        doc(
            "````markdown",
            "```java",
            "class Sample {}",
            "```",
            "````",
            "after `Foo`",
            "~~~",
            "~~~");

    assertThat(lines(doc.fences())).containsExactly("1 markdown", "7 ");
    assertThat(lines(doc.codeSpans())).containsExactly("6 Foo");
  }

  @Test
  void aFenceMayBeInABlockquote() {
    final Doc doc = doc("> ```java", "> class Sample {}", "> ```", "after");

    assertThat(lines(doc.fences())).containsExactly("1 java");
  }

  @Test
  void aQuotedFenceEndsWithTheQuote() {
    final Doc doc = doc("> ```java", "> code", "after `Foo`");

    assertThat(lines(doc.fences())).containsExactly("1 java");
    assertThat(lines(doc.codeSpans())).containsExactly("3 Foo");
  }

  @Test
  void codeInsideAFenceIsNoCodeSpan() {
    final Doc doc = doc("```", "`Foo`", "```");

    assertThat(doc.codeSpans()).isEmpty();
  }

  @Test
  void codeSpansMayUseSeveralBackticks() {
    final Doc doc = doc("Use `` `Foo` `` and ``Map<String, Bar>`` and `Baz`.");

    assertThat(texts(doc.codeSpans())).containsExactly("`Foo`", "Map<String, Bar>", "Baz");
  }

  @Test
  void findsInlineLinkTargetsAndSkipsExternalOnes() {
    final Doc doc =
        doc(
            "[a](a.md 'T') [b](b.md (T)) [c](<c d.md>) [![e](e.png)](f.md) [g](g(h).md)",
            "[i](https://camunda.com) [m](mailto:a@b.c) `[j](j.md)`");

    assertThat(texts(doc.links()))
        .containsExactly("a.md", "b.md", "c d.md", "f.md", "e.png", "g(h).md");
    assertThat(doc.links()).extracting(Ref::line).containsExactly(1, 1, 1, 1, 1, 1);
  }

  @Test
  void linkDestinationsMayEscapeOrNestParentheses() {
    final Doc doc = doc("[a](foo\\)bar.md) [b](foo((bar)).md)");

    assertThat(texts(doc.links())).containsExactly("foo)bar.md", "foo((bar)).md");
  }

  @Test
  void findsReferenceLinkTargetsAndSkipsFootnotes() {
    final Doc doc = doc("[a]: <a b.md>", "[b]: b.md \"T\"", "[^1]: a footnote");

    assertThat(texts(doc.links())).containsExactly("a b.md", "b.md");
  }

  @Test
  void anchorsFollowGitHub() {
    final Doc doc =
        doc(
            "# Title",
            "## Title",
            "## Title",
            "## What's new? (v2)",
            "## The `AgentContext` type",
            "## The _foo_ bar and max_tokens",
            "## Größe Ü",
            "<a id=\"double\"></a> <a name='single'></a>");

    assertThat(doc.anchors())
        .containsExactly(
            "title",
            "title-1",
            "title-2",
            "whats-new-v2",
            "the-agentcontext-type",
            "the-foo-bar-and-max_tokens",
            "größe-ü",
            "double",
            "single");
  }

  @Test
  void anchorsDropOnlyEmphasisUnderscoresOutsideCodeSpans() {
    final Doc doc =
        doc(
            "## `__init__` method",
            "## `_private` field",
            "## Foo_ bar",
            "## The _foo_ bar",
            "## ***Bold italic***",
            "## snake_case_name",
            "## __init__ method");

    assertThat(doc.anchors())
        .containsExactly(
            "__init__-method",
            "_private-field",
            "foo_-bar",
            "the-foo-bar",
            "bold-italic",
            "snake_case_name",
            "init-method");
  }

  @Test
  void headingsAreGivenInAtxFormIncludingSetextHeadings() {
    final Doc doc = doc("Title", "=====", "", "Section", "-------", "", "### Sub `code` ###");

    assertThat(lines(doc.headings()))
        .containsExactly("1 # Title", "4 ## Section", "7 ### Sub code");
    assertThat(doc.anchors()).containsExactly("title", "section", "sub-code");
  }

  @Test
  void frontMatterIsNoHeadingAndKeepsTheLineNumbers() {
    final Doc doc =
        doc("---", "applyTo: \"docs\"", "---", "Title", "=====", "", "---", "Section", "---");

    assertThat(lines(doc.headings())).containsExactly("4 # Title", "8 ## Section");
  }

  @Test
  void listItemsQuotesAndHtmlAreNoSetextHeadings() {
    final Doc doc = doc("- item", "---", "1. item", "---", "> quote", "---", "<div>", "---");

    assertThat(doc.headings()).isEmpty();
  }

  @Test
  void anchorsOfHtmlBlocksCount() {
    final Doc doc = doc("<a id=\"block\"></a>", "", "text <a name=\"inline\"></a>");

    assertThat(doc.anchors()).containsExactly("block", "inline");
  }

  private static Doc doc(final String... lines) {
    return MarkdownParser.parse(Path.of("sample.md"), String.join("\n", lines) + "\n");
  }

  private static List<String> lines(final List<Ref> refs) {
    return refs.stream().map(ref -> ref.line() + " " + ref.text()).toList();
  }

  @Test
  void theCollectionsOfADocCannotBeChanged() {
    final Doc doc = doc("# A", "[a](a.md)", "`Foo`", "```java", "```");

    assertThatThrownBy(() -> doc.headings().add(new Ref(9, "# B")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> doc.links().add(new Ref(9, "b.md")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> doc.codeSpans().add(new Ref(9, "Bar")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> doc.fences().add(new Ref(9, "java")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> doc.anchors().add("b"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void aDocKeepsTheAnchorsInOrderAndIgnoresLaterChangesOfTheSource() {
    final Set<String> source = new LinkedHashSet<>(List.of("b", "a"));
    final Doc doc = new Doc(Path.of("a.md"), List.of(), List.of(), List.of(), List.of(), source);
    source.add("c");

    assertThat(doc.anchors()).containsExactly("b", "a");
  }

  private static List<String> texts(final List<Ref> refs) {
    return refs.stream().map(Ref::text).toList();
  }
}
