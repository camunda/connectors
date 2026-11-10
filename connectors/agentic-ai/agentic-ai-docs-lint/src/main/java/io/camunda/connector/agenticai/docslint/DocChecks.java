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
import java.util.List;
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

  private static final Pattern ILLEGAL_CHARACTER = Pattern.compile("[ \"<>\\[\\\\\\]^`{|}]");

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
            span.typeNames().stream()
                .filter(name -> !index.knows(name) && !allowedNames.contains(name))
                .map(name -> unknownType(doc, span, name, index)));
  }

  /**
   * Each {@code Type#member} in a code span must name a member of a type of the code. Whitespace
   * around the {@code #}, such as a line break inside the span, is ignored.
   */
  static Stream<Finding> members(final Doc doc, final JavaSourceIndex index) {
    return doc.codeSpans().stream()
        .flatMap(span -> span.memberReference().stream())
        .flatMap(reference -> member(doc, reference, index));
  }

  /**
   * The README of the module exists, starts with the module title and the sections, and links every
   * doc of the module under {@code ## Docs}. The title is {@code # <module name>} unless the titles
   * give another one.
   */
  static Stream<Finding> readme(
      final DocModule module,
      final DocTree tree,
      final List<String> sections,
      final Map<String, String> titles) {
    final Path path = module.dir().resolve("README.md");
    final String title = titles.getOrDefault(module.name(), "# " + module.name());
    final Optional<Doc> readme = tree.doc(path);
    if (readme.isEmpty()) {
      return Stream.of(
          new Violation(
              path,
              0,
              "README is missing",
              "a reader starts at the README of a module, and it links the docs of the module",
              "add a README.md that starts with `" + title + "` and has the sections " + sections));
    }
    final List<Finding> findings = new ArrayList<>();
    readme.get().firstHeading(title).ifPresent(findings::add);
    readme.get().sections(sections).ifPresent(findings::add);
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
    docs.forEach(doc -> doc.codeSpans().forEach(span -> named.addAll(span.typeNames())));
    return named;
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
      final Doc doc, final CodeSpan span, final String name, final JavaSourceIndex index) {
    final List<String> similar = index.similarNames(name);
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
      final Doc doc, final CodeSpan.MemberReference reference, final JavaSourceIndex index) {
    final String qualifier = reference.qualifier();
    final String simpleName = reference.simpleName();
    final String member = reference.member();
    final List<JavaSourceIndex.Type> named = index.types(simpleName);
    if (named.isEmpty()) {
      return Stream.of(
          new Warning(
              doc.path(),
              reference.line(),
              "member of `"
                  + reference.text()
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
              reference.line(),
              "qualified name `" + qualifiedName + "` of `" + reference.text() + "` not found",
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
                  reference.line(),
                  "name `"
                      + reference.text()
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
              reference.line(),
              "no matching constructor for `" + reference.text() + "`",
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
              reference.line(),
              "member of `" + reference.text() + "` not verified",
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
            reference.line(),
            candidates.size() > 1
                ? "no type named " + simpleName + " has the member of `" + reference.text() + "`"
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

  private static List<Finding> unlinkedDocs(
      final Doc readme, final DocModule module, final DocTree tree) {
    final List<Path> linked =
        readme.docsLinks().stream()
            .flatMap(link -> target(readme.path(), link.text()).stream())
            .toList();
    return tree.files(module.dir().resolve("docs"), ".md").stream()
        .filter(file -> !linked.contains(file))
        .<Finding>map(file ->
            new Violation(
                readme.path(),
                readme.docsLine(),
                "`## Docs` does not link `" + file + "`",
                "the README lists every doc of the module, so a reader can find it",
                "add a link to the doc under `## Docs`"))
        .toList();
  }
}
