/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.docslint;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checks that keep the docs in step with the modules and the code. The code is the source of truth:
 * a finding says what is wrong, why it matters and what to verify.
 */
final class DocChecks {

  private static final List<String> README_SECTIONS =
      List.of("## Purpose", "## Key types", "## Docs");

  private static final Map<String, String> README_TITLES =
      Map.of("connector-aiagent", "# AI Agent Task");

  private static final Pattern MEMBER_REFERENCE =
      Pattern.compile(
          "^((?:[a-z][a-z0-9_]*\\.)*(?:[A-Z]\\w*\\.)*)([A-Z]\\w*)(?:<.*>)?#(\\w+)(?:\\(.*\\))?$");

  private static final Pattern TYPE_REFERENCE =
      Pattern.compile("^(?:[a-z][a-z0-9_]*\\.)*([A-Z][A-Za-z0-9]*(?:\\.[A-Za-z][A-Za-z0-9]*)*)$");

  private static final Pattern ILLEGAL_CHARACTER = Pattern.compile("[ \"<>\\[\\\\\\]^`{|}]");

  private static final Pattern UPPER_CAMEL = Pattern.compile("[A-Z]\\w*[a-z]\\w*");

  private DocChecks() {}

  /**
   * The relative links of the doc: the file exists and, for a Markdown file, so does the anchor.
   */
  static Stream<Finding> links(final Doc doc, final DocTree tree) {
    return Stream.concat(doc.links().stream(), doc.definitions().stream())
        .flatMap(link -> link(doc, link, tree).stream());
  }

  /** The names of code spans that read as types must be known to the code or allowed. */
  static Stream<Finding> types(
      final Doc doc, final JavaSourceIndex index, final Set<String> allowedNames) {
    return doc.codeSpans().stream()
        .flatMap(span ->
            typeNames(span.text()).stream()
                .filter(name -> !index.knows(name) && !allowedNames.contains(name))
                .map(name -> unknownType(doc, span, name, index)));
  }

  /**
   * Each {@code Type#member} in a code span must name a member of a type of the code. Whitespace
   * around the {@code #}, such as a line break inside the span, is ignored.
   */
  static Stream<Finding> members(final Doc doc, final JavaSourceIndex index) {
    return doc.codeSpans().stream()
        .map(span -> new Ref(span.line(), span.text().replaceAll("\\s*#\\s*", "#")))
        .flatMap(span -> {
          final Matcher matcher = MEMBER_REFERENCE.matcher(span.text());
          return matcher.matches()
              ? member(doc, span, matcher.group(1), matcher.group(2), matcher.group(3), index)
              : Stream.empty();
        });
  }

  /**
   * The README of the module exists, starts with the module title and the fixed sections, and links
   * every doc of the module under {@code ## Docs}.
   */
  static Stream<Finding> readme(final DocModule module, final DocTree tree) {
    final Path path = module.dir().resolve("README.md");
    final Optional<Doc> readme = tree.doc(path);
    if (readme.isEmpty()) {
      return Stream.of(
          new Violation(
              path,
              0,
              "README is missing",
              "a module README tells a reader its purpose, its key types and its docs",
              "add a README.md that starts with `# "
                  + module.name()
                  + "` and has the sections "
                  + README_SECTIONS));
    }
    final List<Finding> findings = new ArrayList<>();
    headings(readme.get(), module).ifPresent(findings::add);
    sections(readme.get()).ifPresent(findings::add);
    findings.addAll(unlinkedDocs(readme.get(), module, tree));
    return findings.stream();
  }

  /** Each allowed name is used in a doc and is not declared by the code. */
  static Stream<Finding> allowedNames(
      final Map<String, String> allowedNames,
      final Set<String> namedInDocs,
      final JavaSourceIndex index,
      final Path source) {
    return new TreeSet<>(allowedNames.keySet())
        .stream()
            .<Finding>flatMap(name -> {
              if (!namedInDocs.contains(name)) {
                return Stream.of(
                    new Violation(
                        source,
                        0,
                        "allowed name `" + name + "` is used in no doc",
                        "an entry that no doc needs keeps a name known that nothing uses",
                        "delete the entry of the allowed names"));
              }
              return index.knows(name)
                  ? Stream.of(
                      new Violation(
                          source,
                          0,
                          "allowed name `" + name + "` is now declared by the code",
                          "the code is the source of truth, and the check of the doc"
                              + " verifies a declared name against it",
                          "delete the entry of the allowed names"))
                  : Stream.empty();
            });
  }

  /** The type names of the code spans of the docs. */
  static Set<String> namedTypes(final Stream<Doc> docs) {
    final Set<String> named = new TreeSet<>();
    docs.forEach(doc -> doc.codeSpans().forEach(span -> named.addAll(typeNames(span.text()))));
    return named;
  }

  /**
   * The UpperCamel names of a code span that reads as a type reference, for example {@code
   * Map<String, Foo>}, {@code @Foo(...)}, {@code Foo#bar}, {@code Foo.Bar}, {@code Foo.java} or
   * {@code io.camunda.Foo}. Generic arguments count too.
   */
  static List<String> typeNames(final String span) {
    final String text = span.replaceFirst("^@", "").replaceFirst("[(#].*$", "");
    String outer = text;
    String previous;
    do {
      previous = outer;
      outer = outer.replaceAll("<[^<>]*>", "");
    } while (!outer.equals(previous));
    final List<String> names = new ArrayList<>(segmentNames(outer));
    if (names.isEmpty() || outer.equals(text)) {
      return names;
    }
    final String arguments = text.substring(text.indexOf('<'));
    for (final String argument : arguments.split("[<>,&\\s]+|\\?|\\bextends\\b|\\bsuper\\b")) {
      names.addAll(segmentNames(argument));
    }
    return names;
  }

  /**
   * The file of a link relative to a doc: without query and fragment, and URL-decoded. A link that
   * starts with a slash is relative to the repository root. Characters that a URI forbids, such as
   * a space or brackets, are percent-encoded first, as GitHub does. Empty when the link is still
   * not a valid URI, for example because of an invalid escape.
   */
  private static Optional<Path> target(final Path from, final String link) {
    return uri(link).map(uri -> resolve(from, uri.getPath()));
  }

  private static Optional<URI> uri(final String link) {
    try {
      return Optional.of(parse(link));
    } catch (final URISyntaxException e) {
      return Optional.empty();
    }
  }

  private static URI parse(final String link) throws URISyntaxException {
    final Matcher matcher = ILLEGAL_CHARACTER.matcher(link);
    return new URI(
        matcher.replaceAll(
            character -> String.format("%%%02X", (int) character.group().charAt(0))));
  }

  private static Path resolve(final Path from, final String path) {
    final Path base =
        path.startsWith("/") || from.getParent() == null ? Path.of("") : from.getParent();
    return base.resolve(path.replaceFirst("^/", "")).normalize();
  }

  /** The GitHub source view, whose fragment is a line anchor such as {@code #L5}. */
  private static boolean plainView(final URI uri) {
    return uri.getRawQuery() != null && List.of(uri.getRawQuery().split("&")).contains("plain=1");
  }

  private static Optional<Violation> link(final Doc doc, final Ref link, final DocTree tree) {
    final String text = link.text();
    final URI uri;
    try {
      uri = parse(text);
    } catch (final URISyntaxException e) {
      return Optional.of(
          new Violation(
              doc.path(),
              link.line(),
              "link `" + text + "` is not a valid URI: " + e.getReason(),
              "the path cannot be decoded, so it names no file",
              "percent-encode the character (a literal % is written %25)"));
    }
    final String path = uri.getPath();
    final String anchor = uri.getFragment() == null || plainView(uri) ? "" : uri.getFragment();
    final Path target = path.isEmpty() ? doc.path() : resolve(doc.path(), path);
    if (target.startsWith("..")) {
      return Optional.of(
          new Violation(
              doc.path(),
              link.line(),
              "link `" + text + "` leaves the repository",
              "the target is outside the repository, so a reader of a checkout cannot open it",
              "link a file inside the repository"));
    }
    if (!path.isEmpty() && !tree.exists(target)) {
      return Optional.of(
          new Violation(
              doc.path(),
              link.line(),
              "link `"
                  + text
                  + "` points to a file that does not exist (resolved to `"
                  + target
                  + "`)",
              "the file was renamed, moved or deleted, or the path is wrong",
              "check the path against the file tree"));
    }
    if (anchor.isEmpty() || !target.toString().endsWith(".md")) {
      return Optional.empty();
    }
    final Optional<Doc> targetDoc = path.isEmpty() ? Optional.of(doc) : tree.doc(target);
    if (targetDoc.isPresent() && !targetDoc.get().anchors().contains(anchor)) {
      return Optional.of(
          new Violation(
              doc.path(),
              link.line(),
              "link `" + text + "` points to an anchor that does not exist in `" + target + "`",
              "the heading was renamed or removed, or GitHub derives a different anchor from it",
              "check the headings of the target. Its anchors are: "
                  + String.join(", ", targetDoc.get().anchors())));
    }
    return Optional.empty();
  }

  private static Violation unknownType(
      final Doc doc, final Ref span, final String name, final JavaSourceIndex index) {
    final List<String> similar = similarNames(name, index.declaredNames());
    return new Violation(
        doc.path(),
        span.line(),
        "unknown type `" + name + "` in `" + span.text() + "`",
        "the code declares, imports or nests no type of this name, so the doc may name a type that"
            + " was renamed or removed",
        "check the name against the code"
            + (similar.isEmpty()
                ? ""
                : ". Similar names in the code (they may be unrelated): "
                    + String.join(", ", similar)));
  }

  private static Stream<Finding> member(
      final Doc doc,
      final Ref span,
      final String qualifier,
      final String simpleName,
      final String member,
      final JavaSourceIndex index) {
    final List<JavaSourceIndex.Type> named = index.types(simpleName);
    if (named.isEmpty()) {
      return Stream.of(
          new Warning(
              doc.path(),
              span.line(),
              "member of `"
                  + span.text()
                  + "` not verified: `"
                  + simpleName
                  + "` is not declared in the repo sources, so it is a type of a dependency whose"
                  + " classes are not loaded",
              "the check reads the code of the repo, so it cannot see the members of a dependency",
              "check the member against the documentation or the sources of the dependency"));
    }
    final String qualifiedName = qualifier + simpleName;
    final List<JavaSourceIndex.Type> candidates =
        qualifier.isEmpty()
            ? named
            : named.stream()
                .filter(type ->
                    type.qualifiedName().equals(qualifiedName)
                        || type.qualifiedName().endsWith("." + qualifiedName))
                .toList();
    final String names = qualifiedNames(named);
    if (candidates.isEmpty()) {
      return Stream.of(
          new Violation(
              doc.path(),
              span.line(),
              "qualified name `" + qualifiedName + "` of `" + span.text() + "` not found",
              "no type of the repo sources has this qualified name",
              "check the package and outer types. The types named "
                  + simpleName
                  + " are: "
                  + names));
    }
    if (candidates.stream().anyMatch(type -> hasMember(type, member, index))
        || candidates.stream().anyMatch(type -> inheritsMember(type, member, index))) {
      return candidates.size() > 1
          ? Stream.of(
              new Warning(
                  doc.path(),
                  span.line(),
                  "name `"
                      + span.text()
                      + "` is ambiguous and was checked against all types of that name: "
                      + names,
                  "the member exists on one of the types of that name, and the doc does not say which",
                  "write the qualified name to pin one of them"))
          : Stream.empty();
    }
    if (member.equals(simpleName)) {
      return Stream.of(
          new Violation(
              doc.path(),
              span.line(),
              "no matching constructor for `" + span.text() + "`",
              "the type of this name is an interface, and an interface has no constructor",
              "check the kind of the type in the code: " + names));
    }
    final Map<String, String> unloaded = new TreeMap<>();
    candidates.forEach(type ->
        index
            .undeclaredSupertypes(type)
            .forEach((supertype, extending) -> {
              if (index.jdkMembers(supertype).isEmpty()) {
                unloaded.putIfAbsent(supertype, extending);
              }
            }));
    if (!unloaded.isEmpty()) {
      final Map.Entry<String, String> external = unloaded.entrySet().iterator().next();
      return Stream.of(
          new Warning(
              doc.path(),
              span.line(),
              "member of `" + span.text() + "` not verified",
              external.getValue()
                  + " extends "
                  + external.getKey()
                  + ", an external dependency type that is not loaded, so the member may be"
                  + " inherited",
              "name the declaring type or check the member in the library"));
    }
    final Set<String> members = new TreeSet<>();
    candidates.forEach(type -> members.addAll(index.members(type)));
    return Stream.of(
        new Violation(
            doc.path(),
            span.line(),
            candidates.size() > 1
                ? "no type named " + simpleName + " has the member of `" + span.text() + "`"
                : "no such member `"
                    + member
                    + "` on `"
                    + candidates.getFirst().qualifiedName()
                    + "`",
            "the member was renamed or removed, or is spelled differently",
            "check the member against the code"
                + (candidates.size() > 1
                    ? ". The types named "
                        + simpleName
                        + " are: "
                        + names
                        + ". Write the qualified name to pin one of them"
                    : "")
                + (members.isEmpty()
                    ? ". The type has no members"
                    : ". Members, including those of supertypes of the repo: "
                        + String.join(", ", members))));
  }

  private static boolean hasMember(
      final JavaSourceIndex.Type type, final String member, final JavaSourceIndex index) {
    return member.equals(type.simpleName())
        ? !type.isInterface()
        : index.members(type).contains(member);
  }

  private static boolean inheritsMember(
      final JavaSourceIndex.Type type, final String member, final JavaSourceIndex index) {
    return (type.isEnum() && member.equals("values"))
        || index.undeclaredSupertypes(type).keySet().stream()
            .anyMatch(
                name -> index.jdkMembers(name).map(names -> names.contains(member)).orElse(false));
  }

  private static String qualifiedNames(final List<JavaSourceIndex.Type> types) {
    return String.join(
        ", ", types.stream().map(JavaSourceIndex.Type::qualifiedName).sorted().toList());
  }

  private static Optional<Violation> headings(final Doc readme, final DocModule module) {
    final String title = README_TITLES.getOrDefault(module.name(), "# " + module.name());
    final List<Ref> headings = readme.headings();
    if (!headings.isEmpty() && headings.getFirst().text().equals(title)) {
      return Optional.empty();
    }
    return Optional.of(
        new Violation(
            readme.path(),
            headings.isEmpty() ? 1 : headings.getFirst().line(),
            "first heading is "
                + (headings.isEmpty() ? "missing" : "`" + headings.getFirst().text() + "`")
                + ", expected `"
                + title
                + "`",
            "the first heading names the module, so a reader and a search find the README by it",
            "start the README with `" + title + "`"));
  }

  private static Optional<Violation> sections(final Doc readme) {
    final List<Ref> sections =
        readme.headings().stream()
            .filter(heading -> heading.text().startsWith("## "))
            .limit(README_SECTIONS.size())
            .toList();
    final List<String> actual = sections.stream().map(Ref::text).toList();
    if (actual.equals(README_SECTIONS)) {
      return Optional.empty();
    }
    return Optional.of(
        new Violation(
            readme.path(),
            sections.isEmpty() ? 1 : sections.getFirst().line(),
            "the first sections are " + actual + ", expected " + README_SECTIONS,
            "all module READMEs share one layout, so a reader finds the same section in each",
            "put the sections in this order before any other `##` section"));
  }

  private static List<Finding> unlinkedDocs(
      final Doc readme, final DocModule module, final DocTree tree) {
    final Optional<Ref> docs =
        readme.headings().stream().filter(heading -> heading.text().equals("## Docs")).findFirst();
    final int end =
        docs.flatMap(section ->
                readme.headings().stream()
                    .filter(h -> h.line() > section.line() && h.text().startsWith("## "))
                    .findFirst())
            .map(Ref::line)
            .orElse(Integer.MAX_VALUE);
    final List<Path> linked =
        readme.links().stream()
            .filter(
                link -> docs.isPresent() && link.line() > docs.get().line() && link.line() < end)
            .flatMap(link -> target(readme.path(), link.text()).stream())
            .toList();
    return tree.files(module.dir().resolve("docs"), ".md").stream()
        .filter(file -> !linked.contains(file))
        .<Finding>map(file ->
            new Violation(
                readme.path(),
                docs.map(Ref::line).orElse(1),
                "`## Docs` does not link `" + file + "`",
                "the README lists every doc of the module, so a reader can find it",
                "add a link to the doc under `## Docs`"))
        .toList();
  }

  private static List<String> segmentNames(final String reference) {
    final String text =
        reference.strip().replaceFirst("(\\[]|\\.\\.\\.)$", "").replaceFirst("\\.java$", "");
    final Matcher matcher = TYPE_REFERENCE.matcher(text);
    if (!matcher.matches()) {
      return List.of();
    }
    final List<String> names = new ArrayList<>();
    for (final String segment : matcher.group(1).split("\\.")) {
      if (UPPER_CAMEL.matcher(segment).matches()) {
        names.add(segment);
      }
    }
    return names;
  }

  private static List<String> similarNames(final String name, final Set<String> candidates) {
    final String lower = name.toLowerCase(Locale.ROOT);
    return candidates.stream()
        .filter(candidate -> {
          final String other = candidate.toLowerCase(Locale.ROOT);
          return other.contains(lower)
              || (other.length() > 3 && lower.contains(other))
              || distance(lower, other) <= 2;
        })
        .sorted(
            Comparator.comparingInt(
                    (final String candidate) -> distance(lower, candidate.toLowerCase(Locale.ROOT)))
                .thenComparing(Comparator.naturalOrder()))
        .limit(5)
        .toList();
  }

  private static int distance(final String a, final String b) {
    int[] previous = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) {
      previous[j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      final int[] current = new int[b.length() + 1];
      current[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        current[j] =
            Math.min(
                Math.min(current[j - 1], previous[j]) + 1,
                previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
      }
      previous = current;
    }
    return previous[b.length()];
  }
}
