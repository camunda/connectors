/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.docslint;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A code span of a document and the line it is on, counted from 1. The text is stripped. */
record CodeSpan(int line, String text) {

  private static final Pattern MEMBER_REFERENCE =
      Pattern.compile(
          "^((?:[a-z][a-z0-9_]*\\.)*(?:[A-Z]\\w*\\.)*)([A-Z]\\w*)(?:<.*>)?#(\\w+)(?:\\(.*\\))?$");

  private static final Pattern TYPE_REFERENCE =
      Pattern.compile("^((?:[a-z][a-z0-9_]*\\.)*)([A-Z][A-Za-z0-9]*(?:\\.[A-Za-z][A-Za-z0-9]*)*)$");

  private static final Pattern UPPER_CAMEL = Pattern.compile("[A-Z]\\w*[a-z]\\w*");

  private static final Pattern TYPE_ARGUMENT_SEPARATOR =
      Pattern.compile("[<>,&\\s]+|\\?|\\bextends\\b|\\bsuper\\b");

  private static final Pattern DOT = Pattern.compile("\\.");

  /**
   * A {@code Type#member} reference. The text has no whitespace around the {@code #}. The qualifier
   * holds the package and the outer types, each with a trailing dot, and is empty without them.
   */
  record MemberReference(
      int line, String text, String qualifier, String simpleName, String member) {}

  /**
   * The UpperCamel names of the span when it reads as a type reference, for example {@code
   * Map<String, Foo>}, {@code @Foo(...)}, {@code Foo#bar}, {@code Foo.Bar}, {@code Foo.java} or
   * {@code io.camunda.Foo}. Generic arguments count too.
   */
  List<String> typeNames() {
    return references().stream().flatMap(reference -> segmentNames(reference).stream()).toList();
  }

  /**
   * The qualified type names of the span, for example {@code io.camunda.Foo} or {@code Foo.Bar}:
   * the package and the UpperCamel names up to the first other name. Generic arguments count too. A
   * name without a package or an outer type is left out.
   */
  List<String> qualifiedTypeNames() {
    return references().stream().flatMap(reference -> qualifiedName(reference).stream()).toList();
  }

  /**
   * The {@code Type#member} reference of the span, or empty when the span is none. Whitespace
   * around the {@code #}, such as a line break inside the span, is ignored.
   */
  Optional<MemberReference> memberReference() {
    final String reference = text.replaceAll("\\s*#\\s*", "#");
    final Matcher matcher = MEMBER_REFERENCE.matcher(reference);
    return matcher.matches()
        ? Optional.of(
            new MemberReference(
                line, reference, matcher.group(1), matcher.group(2), matcher.group(3)))
        : Optional.empty();
  }

  private List<String> references() {
    final String reference = text.replaceFirst("^@", "").replaceFirst("[(#].*$", "");
    String outer = reference;
    String previous;
    do {
      previous = outer;
      outer = outer.replaceAll("<[^<>]*>", "");
    } while (!outer.equals(previous));
    final List<String> references = new ArrayList<>(List.of(outer));
    if (segmentNames(outer).isEmpty() || outer.equals(reference)) {
      return references;
    }
    TYPE_ARGUMENT_SEPARATOR
        .splitAsStream(reference.substring(reference.indexOf('<')))
        .forEach(references::add);
    return references;
  }

  private static List<String> segmentNames(final String reference) {
    return typeReference(reference)
        .map(matcher ->
            DOT.splitAsStream(matcher.group(2)).filter(UPPER_CAMEL.asMatchPredicate()).toList())
        .orElse(List.of());
  }

  private static Optional<String> qualifiedName(final String reference) {
    return typeReference(reference)
        .map(matcher ->
            matcher.group(1)
                + String.join(
                    ".",
                    DOT.splitAsStream(matcher.group(2))
                        .takeWhile(UPPER_CAMEL.asMatchPredicate())
                        .toList()))
        .filter(name -> name.contains(".") && !name.endsWith("."));
  }

  private static Optional<Matcher> typeReference(final String reference) {
    final String text =
        reference.strip().replaceFirst("(\\[]|\\.\\.\\.)$", "").replaceFirst("\\.java$", "");
    final Matcher matcher = TYPE_REFERENCE.matcher(text);
    return matcher.matches() ? Optional.of(matcher) : Optional.empty();
  }
}
