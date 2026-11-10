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
      Pattern.compile("^(?:[a-z][a-z0-9_]*\\.)*([A-Z][A-Za-z0-9]*(?:\\.[A-Za-z][A-Za-z0-9]*)*)$");

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
    final String reference = text.replaceFirst("^@", "").replaceFirst("[(#].*$", "");
    String outer = reference;
    String previous;
    do {
      previous = outer;
      outer = outer.replaceAll("<[^<>]*>", "");
    } while (!outer.equals(previous));
    final List<String> names = new ArrayList<>(segmentNames(outer));
    if (names.isEmpty() || outer.equals(reference)) {
      return names;
    }
    final String arguments = reference.substring(reference.indexOf('<'));
    TYPE_ARGUMENT_SEPARATOR
        .splitAsStream(arguments)
        .forEach(argument -> names.addAll(segmentNames(argument)));
    return names;
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

  private static List<String> segmentNames(final String reference) {
    final String text =
        reference.strip().replaceFirst("(\\[]|\\.\\.\\.)$", "").replaceFirst("\\.java$", "");
    final Matcher matcher = TYPE_REFERENCE.matcher(text);
    if (!matcher.matches()) {
      return List.of();
    }
    return DOT.splitAsStream(matcher.group(1)).filter(UPPER_CAMEL.asMatchPredicate()).toList();
  }
}
