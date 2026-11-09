/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.docslint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocChecksTest {

  private static final Path DIR = Path.of("connectors/agentic-ai/one");
  private static final DocModule MODULE = new DocModule("one", DIR);

  private static final JavaSourceIndex SOURCES =
      JavaSourceIndex.of(
          Map.of(
              "a/Base.java",
              "package a; class Base { void inherited() {} }",
              "a/Service.java",
              "package a; import java.util.List; import java.util.Map; import java.util.Optional;"
                  + " import b.External;"
                  + " class Service extends Base implements Api {"
                  + " Service(int x) {} void run() {} int field; record Nested(String part) {} }",
              "a/Api.java",
              "package a; interface Api { void api(); }",
              "a/Dup.java",
              "package a; class Dup { void first() {} }",
              "b/Dup.java",
              "package b; class Dup { void second() {} }"));

  @TempDir Path root;

  @Test
  void aReadmeWithTheLayoutAndLinkedDocsHasNoFindings() throws IOException {
    write("one/README.md", readme("# one", "- [Doc](docs/a.md)", "", "## More"));
    write("one/docs/a.md", "# a");

    assertThat(DocChecks.readme(MODULE, new DocTree(root))).isEmpty();
  }

  @Test
  void aMissingReadmeIsReported() {
    assertThat(messages(DocChecks.readme(MODULE, new DocTree(root))))
        .containsExactly(
            """
            connectors/agentic-ai/one/README.md: README is missing
                why: a module README tells a reader its purpose, its key types and its docs
                fix: add a README.md that starts with `# one` and has the sections [## Purpose, ## Key types, ## Docs]\
            """);
  }

  @Test
  void aWrongTitleIsReportedAndAnOverrideIsAccepted() throws IOException {
    write("one/README.md", "# other\n\n## Purpose\n\n## Key types\n\n## Docs\n");
    write("aiagent/README.md", "# AI Agent Task\n\n## Purpose\n\n## Key types\n\n## Docs\n");

    assertThat(messages(DocChecks.readme(MODULE, new DocTree(root))))
        .containsExactly(
            """
            connectors/agentic-ai/one/README.md:1: first heading is `# other`, expected `# one`
                why: the first heading names the module, so a reader and a search find the README by it
                fix: start the README with `# one`\
            """);
    assertThat(
            DocChecks.readme(
                new DocModule("connector-aiagent", Path.of("connectors/agentic-ai/aiagent")),
                new DocTree(root)))
        .isEmpty();
  }

  @Test
  void wrongSectionsAreReported() throws IOException {
    write("one/README.md", "# one\n\n## Purpose\n\n## Docs\n\n## Key types\n");

    assertThat(messages(DocChecks.readme(MODULE, new DocTree(root))))
        .containsExactly(
            """
            connectors/agentic-ai/one/README.md:3: the first sections are [## Purpose, ## Docs, ## Key types], expected [## Purpose, ## Key types, ## Docs]
                why: all module READMEs share one layout, so a reader finds the same section in each
                fix: put the sections in this order before any other `##` section\
            """);
  }

  @Test
  void aDocThatTheReadmeDoesNotLinkUnderDocsIsReported() throws IOException {
    write(
        "one/README.md",
        "# one\n\n## Purpose\n\n[Doc](docs/a.md)\n\n## Key types\n\n## Docs\n\n- [B](docs/b.md)\n\n## More\n\n[C](docs/c.md)\n");
    write("one/docs/a.md", "# a");
    write("one/docs/b.md", "# b");
    write("one/docs/c.md", "# c");

    assertThat(messages(DocChecks.readme(MODULE, new DocTree(root))))
        .containsExactly(
            """
            connectors/agentic-ai/one/README.md:9: `## Docs` does not link `connectors/agentic-ai/one/docs/a.md`
                why: the README lists every doc of the module, so a reader can find it
                fix: add a link to the doc under `## Docs`\
            """,
            """
            connectors/agentic-ai/one/README.md:9: `## Docs` does not link `connectors/agentic-ai/one/docs/c.md`
                why: the README lists every doc of the module, so a reader can find it
                fix: add a link to the doc under `## Docs`\
            """);
  }

  @Test
  void aDefinitionUnderDocsWithoutALinkDoesNotCountAsALink() throws IOException {
    write(
        "one/README.md", "# one\n\n## Purpose\n\n## Key types\n\n## Docs\n\n[topic]: docs/a.md\n");
    write("one/docs/a.md", "# a");

    assertThat(messages(DocChecks.readme(MODULE, new DocTree(root))))
        .containsExactly(
            """
            connectors/agentic-ai/one/README.md:7: `## Docs` does not link `connectors/agentic-ai/one/docs/a.md`
                why: the README lists every doc of the module, so a reader can find it
                fix: add a link to the doc under `## Docs`\
            """);
  }

  @Test
  void aReferenceLinkUnderDocsCountsWhereverItsDefinitionIs() throws IOException {
    write(
        "one/README.md",
        "# one\n\n## Purpose\n\n## Key types\n\n## Docs\n\n- [A][topic]\n\n## More\n\n[topic]: docs/a.md\n");
    write("one/docs/a.md", "# a");

    assertThat(DocChecks.readme(MODULE, new DocTree(root))).isEmpty();
  }

  @Test
  void aReferenceLinkOutsideDocsDoesNotCountEvenWithItsDefinitionUnderDocs() throws IOException {
    write(
        "one/README.md",
        "# one\n\n## Purpose\n\n[A][topic]\n\n## Key types\n\n## Docs\n\n[topic]: docs/a.md\n");
    write("one/docs/a.md", "# a");

    assertThat(messages(DocChecks.readme(MODULE, new DocTree(root))))
        .containsExactly(
            """
            connectors/agentic-ai/one/README.md:9: `## Docs` does not link `connectors/agentic-ai/one/docs/a.md`
                why: the README lists every doc of the module, so a reader can find it
                fix: add a link to the doc under `## Docs`\
            """);
  }

  @Test
  void aBrokenDefinitionIsReported() throws IOException {
    final Doc doc = doc("one/docs/source.md", "# Source", "[a]: gone.md");

    assertThat(messages(DocChecks.links(doc, new DocTree(root))))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/source.md:2: link `gone.md` points to a file that does not exist (resolved to `connectors/agentic-ai/one/docs/gone.md`)
                why: the file was renamed, moved or deleted, or the path is wrong
                fix: check the path against the file tree\
            """);
  }

  @Test
  void aSchemeRelativeLinkIsExternalAndARootRelativeLinkIsNot() {
    final Doc doc =
        doc("one/docs/source.md", "# Source", "[a](//example.com/guide.md) [b](/guide.md)");

    assertThat(messages(DocChecks.links(doc, new DocTree(root))))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/source.md:2: link `/guide.md` points to a file that does not exist (resolved to `guide.md`)
                why: the file was renamed, moved or deleted, or the path is wrong
                fix: check the path against the file tree\
            """);
  }

  @Test
  void relativeLinksThatResolveHaveNoFindings() throws IOException {
    write("one/docs/target.md", "# Target\n## Section\n");
    write("one/docs/a b+c.md", "# Spaced");
    Files.writeString(root.resolve("top.md"), "# Top");
    final Doc doc =
        doc(
            "one/docs/source.md",
            "# Source",
            "[a](target.md#section) [b](a%20b%2Bc.md) [c](/top.md) [d](#source)",
            "[e](target.md?plain=1#L5) [f](../docs) [g](https://example.com/missing)");

    assertThat(DocChecks.links(doc, new DocTree(root))).isEmpty();
  }

  @Test
  void aMissingFileIsReported() throws IOException {
    final Doc doc = doc("one/docs/source.md", "# Source", "[a](gone.md)");

    assertThat(messages(DocChecks.links(doc, new DocTree(root))))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/source.md:2: link `gone.md` points to a file that does not exist (resolved to `connectors/agentic-ai/one/docs/gone.md`)
                why: the file was renamed, moved or deleted, or the path is wrong
                fix: check the path against the file tree\
            """);
  }

  @Test
  void aBrokenAnchorListsTheAnchorsOfTheTarget() throws IOException {
    write("one/docs/target.md", "# Target\n## Section\n");
    final Doc doc = doc("one/docs/source.md", "# Source", "[a](target.md#nope) [b](#nope)");

    assertThat(messages(DocChecks.links(doc, new DocTree(root))))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/source.md:2: link `target.md#nope` points to an anchor that does not exist in `connectors/agentic-ai/one/docs/target.md`
                why: the heading was renamed or removed, or GitHub derives a different anchor from it
                fix: check the headings of the target. Its anchors are: target, section\
            """,
            """
            connectors/agentic-ai/one/docs/source.md:2: link `#nope` points to an anchor that does not exist in `connectors/agentic-ai/one/docs/source.md`
                why: the heading was renamed or removed, or GitHub derives a different anchor from it
                fix: check the headings of the target. Its anchors are: source\
            """);
  }

  @Test
  void aBadEscapeIsReported() {
    final Doc doc = doc("one/docs/source.md", "# Source", "[a](%zz.md)");

    assertThat(messages(DocChecks.links(doc, new DocTree(root))))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/source.md:2: link `%zz.md` is not a valid URI: Malformed escape pair
                why: the path cannot be decoded, so it names no file
                fix: percent-encode the character (a literal % is written %25)\
            """);
  }

  @Test
  void aLinkWithCharactersThatAUriForbidsResolvesToTheFile() throws IOException {
    write("one/docs/a[b].md", "# Brackets");
    write("one/docs/{c}|d.md", "# Others");
    final Doc doc = doc("one/docs/source.md", "# Source", "[a](a[b].md) [b](<{c}|d.md>)");

    assertThat(messages(DocChecks.links(doc, new DocTree(root)))).isEmpty();
  }

  @Test
  void aLinkWithBracketsToAMissingFileNamesTheResolvedFile() {
    final Doc doc = doc("one/docs/source.md", "# Source", "[a](a[b].md)");

    assertThat(messages(DocChecks.links(doc, new DocTree(root))))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/source.md:2: link `a[b].md` points to a file that does not exist (resolved to `connectors/agentic-ai/one/docs/a[b].md`)
                why: the file was renamed, moved or deleted, or the path is wrong
                fix: check the path against the file tree\
            """);
  }

  @Test
  void aLinkThatLeavesTheRepositoryIsReported() {
    final Doc doc = doc("one/docs/source.md", "# Source", "[a](../../../../../outside.md)");

    assertThat(messages(DocChecks.links(doc, new DocTree(root))))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/source.md:2: link `../../../../../outside.md` leaves the repository
                why: the target is outside the repository, so a reader of a checkout cannot open it
                fix: link a file inside the repository\
            """);
  }

  @Test
  void aPlainViewLinkHasNoAnchorCheck() throws IOException {
    write("one/docs/target.md", "# Target");
    final Doc doc =
        doc("one/docs/source.md", "# Source", "[a](target.md?plain=1#L5) [b](target.md#L5)");

    assertThat(messages(DocChecks.links(doc, new DocTree(root))))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/source.md:2: link `target.md#L5` points to an anchor that does not exist in `connectors/agentic-ai/one/docs/target.md`
                why: the heading was renamed or removed, or GitHub derives a different anchor from it
                fix: check the headings of the target. Its anchors are: target\
            """);
  }

  @Test
  void aQueryOtherThanPlainViewKeepsTheAnchorCheck() throws IOException {
    write("one/docs/target.md", "# Target\n## Existing\n");
    final Doc doc = doc("one/docs/source.md", "# Source", "[a](target.md?foo=bar#missing)");

    assertThat(messages(DocChecks.links(doc, new DocTree(root))))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/source.md:2: link `target.md?foo=bar#missing` points to an anchor that does not exist in `connectors/agentic-ai/one/docs/target.md`
                why: the heading was renamed or removed, or GitHub derives a different anchor from it
                fix: check the headings of the target. Its anchors are: target, existing\
            """);
  }

  @Test
  void aQueryOtherThanPlainViewPassesWithAnExistingAnchor() throws IOException {
    write("one/docs/target.md", "# Target\n## Existing\n");
    final Doc doc = doc("one/docs/source.md", "# Source", "[a](target.md?foo=bar#existing)");

    assertThat(DocChecks.links(doc, new DocTree(root))).isEmpty();
  }

  @Test
  void aPlainViewAmongOtherParametersHasNoAnchorCheck() throws IOException {
    write("one/docs/target.md", "# Target");
    final Doc doc = doc("one/docs/source.md", "# Source", "[a](target.md?plain=1&foo=bar#L5)");

    assertThat(DocChecks.links(doc, new DocTree(root))).isEmpty();
  }

  @Test
  void aPlainParameterThatIsNotOneKeepsTheAnchorCheck() throws IOException {
    write("one/docs/target.md", "# Target");
    final Doc doc = doc("one/docs/source.md", "# Source", "[a](target.md?plain=0#missing)");

    assertThat(messages(DocChecks.links(doc, new DocTree(root))))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/source.md:2: link `target.md?plain=0#missing` points to an anchor that does not exist in `connectors/agentic-ai/one/docs/target.md`
                why: the heading was renamed or removed, or GitHub derives a different anchor from it
                fix: check the headings of the target. Its anchors are: target\
            """);
  }

  @Test
  void anEncodedFragmentMatchesTheDecodedAnchor() throws IOException {
    write("one/docs/target.md", "# Target\n## Größe\n");
    final Doc doc = doc("one/docs/source.md", "# Source", "[a](target.md#gr%C3%B6%C3%9Fe)");

    assertThat(messages(DocChecks.links(doc, new DocTree(root)))).isEmpty();
  }

  @Test
  void anEncodedFragmentThatMatchesNoAnchorIsReported() throws IOException {
    write("one/docs/target.md", "# Target\n## Größe\n");
    final Doc doc = doc("one/docs/source.md", "# Source", "[a](target.md#gr%C3%BCsse)");

    assertThat(messages(DocChecks.links(doc, new DocTree(root))))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/source.md:2: link `target.md#gr%C3%BCsse` points to an anchor that does not exist in `connectors/agentic-ai/one/docs/target.md`
                why: the heading was renamed or removed, or GitHub derives a different anchor from it
                fix: check the headings of the target. Its anchors are: target, größe\
            """);
  }

  @Test
  void aPlainViewLineAnchorHasNoFindings() throws IOException {
    write("one/docs/target.md", "# Target");
    final Doc doc = doc("one/docs/source.md", "# Source", "[a](target.md?plain=1#L5)");

    assertThat(DocChecks.links(doc, new DocTree(root))).isEmpty();
  }

  @Test
  void aLinkToADirectoryWithATrailingSlashHasNoFindings() throws IOException {
    write("one/docs/target.md", "# Target");
    final Doc doc = doc("one/docs/source.md", "# Source", "[a](../docs/)");

    assertThat(messages(DocChecks.links(doc, new DocTree(root)))).isEmpty();
  }

  @Test
  void aFragmentOnlyLinkIsCheckedAgainstTheAnchorsOfTheDoc() {
    final Doc doc = doc("one/docs/source.md", "# Source", "[a](#source) [b](#nope)");

    assertThat(messages(DocChecks.links(doc, new DocTree(root))))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/source.md:2: link `#nope` points to an anchor that does not exist in `connectors/agentic-ai/one/docs/source.md`
                why: the heading was renamed or removed, or GitHub derives a different anchor from it
                fix: check the headings of the target. Its anchors are: source\
            """);
  }

  @Test
  void aDestinationWithSpacesInAngleBracketsResolvesToTheFile() throws IOException {
    write("one/docs/c d.md", "# Spaced\n## Part\n");
    final Doc doc = doc("one/docs/source.md", "# Source", "[a](<c d.md#part>) [b](<c d.md>)");

    assertThat(messages(DocChecks.links(doc, new DocTree(root)))).isEmpty();
  }

  @Test
  void aPlusInTheLinkStaysAPlusInTheFileName() throws IOException {
    write("one/docs/a+b.md", "# Plus");
    final Doc doc = doc("one/docs/source.md", "# Source", "[a](a+b.md)");

    assertThat(messages(DocChecks.links(doc, new DocTree(root)))).isEmpty();
  }

  @Test
  void aJavaFenceIsReportedInAQuoteToo() {
    final Doc doc =
        doc(
            "one/docs/a.md",
            "# A",
            "```java",
            "int x;",
            "```",
            "> ```Java",
            "> int y;",
            "> ```",
            "```json",
            "```");

    assertThat(messages(doc.javaFences()))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/a.md:2: Java code block
                why: a code excerpt goes stale when the code changes
                fix: replace the excerpt with the name of the type and member, for example `Type#member`\
            """,
            """
            connectors/agentic-ai/one/docs/a.md:5: Java code block
                why: a code excerpt goes stale when the code changes
                fix: replace the excerpt with the name of the type and member, for example `Type#member`\
            """);
  }

  @Test
  void namesOfTheCodeAreKnownAndOthersAreReportedWithSimilarNames() {
    final Doc doc =
        doc("one/docs/a.md", "`Service`, `Map<String, Service>`, `a.Api`, `Servise`, `Allowed`");

    assertThat(messages(DocChecks.types(doc, SOURCES, Set.of("Allowed"))))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/a.md:1: unknown type `Servise` in `Servise`
                why: the code declares, imports or nests no type of this name, so the doc may name a type that was renamed or removed
                fix: check the name against the code. Similar names in the code (they may be unrelated): Service\
            """);
  }

  @Test
  void similarNamesAreTheFiveClosestWithTiesInAlphabeticalOrder() {
    final JavaSourceIndex sources =
        JavaSourceIndex.of(
            Map.of(
                "p/Names.java",
                "package p; class AAWidget {} class ABWidget {} class ACWidget {}"
                    + " class BWidget {} class CWidget {} class Widgets {} class Unrelated {}"));
    final Doc doc = doc("one/docs/a.md", "`Widget`");

    assertThat(messages(DocChecks.types(doc, sources, Set.of())))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/a.md:1: unknown type `Widget` in `Widget`
                why: the code declares, imports or nests no type of this name, so the doc may name a type that was renamed or removed
                fix: check the name against the code. Similar names in the code (they may be unrelated): BWidget, CWidget, Widgets, AAWidget, ABWidget\
            """);
  }

  @Test
  void genericArgumentsAreChecked() {
    final Doc doc = doc("one/docs/a.md", "`List<Missing>` and `Optional<? extends Service>`");

    assertThat(messages(DocChecks.types(doc, SOURCES, Set.of())))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/a.md:1: unknown type `Missing` in `List<Missing>`
                why: the code declares, imports or nests no type of this name, so the doc may name a type that was renamed or removed
                fix: check the name against the code\
            """);
  }

  @Test
  void typeNamesFollowTheConventions() {
    assertThat(DocChecks.typeNames("Foo<Alpha>.Bar<Beta>"))
        .containsExactly("Foo", "Bar", "Alpha", "Beta");
    assertThat(DocChecks.typeNames("@Foo(x = 1)")).containsExactly("Foo");
    assertThat(DocChecks.typeNames("io.camunda.Foo.Bar#baz")).containsExactly("Foo", "Bar");
    assertThat(DocChecks.typeNames("Foo.java")).containsExactly("Foo");
    assertThat(DocChecks.typeNames("Foo[]")).containsExactly("Foo");
    assertThat(DocChecks.typeNames("List<Foo> and Map<Bar>")).isEmpty();
    assertThat(DocChecks.typeNames("MAX_TOKENS")).isEmpty();
  }

  @Test
  void memberReferencesResolveWithInheritedMembersAndConstructors() {
    final Doc doc =
        doc(
            "one/docs/a.md",
            "`Service#run` `Service#field()` `Service#Service(int)` `Service#inherited`",
            "`Service#api` `Service.Nested#part` `a.Service#run` `Nested#part` `Api#api`");

    assertThat(DocChecks.members(doc, SOURCES)).isEmpty();
  }

  @Test
  void aMissingMemberListsTheMembers() {
    final Doc doc = doc("one/docs/a.md", "# A", "`Service#gone()`");

    assertThat(messages(DocChecks.members(doc, SOURCES)))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/a.md:2: no such member `gone` on `a.Service`
                why: the member was renamed or removed, or is spelled differently
                fix: check the member against the code. Members, including those of supertypes of the repo: Service, api, field, inherited, run\
            """);
  }

  @Test
  void aWrappedReferenceToAnExistingMemberHasNoFindings() {
    final Doc doc = doc("one/docs/a.md", "`Service", "#run` `Service#", "run()` `Service  #  run`");

    assertThat(DocChecks.members(doc, SOURCES)).isEmpty();
  }

  @Test
  void aWrappedReferenceHasItsTypeNameChecked() {
    final Doc doc = doc("one/docs/a.md", "`Service", "#run`", "`Missing", "#run`");

    assertThat(messages(DocChecks.types(doc, SOURCES, Set.of())))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/a.md:3: unknown type `Missing` in `Missing #run`
                why: the code declares, imports or nests no type of this name, so the doc may name a type that was renamed or removed
                fix: check the name against the code\
            """);
  }

  @Test
  void aWrappedReferenceToAMissingMemberIsReportedLikeTheUnwrappedForm() {
    final Doc wrapped = doc("one/docs/a.md", "# A", "`Service", "#gone()`");
    final Doc unwrapped = doc("one/docs/a.md", "# A", "`Service#gone()`");

    assertThat(messages(DocChecks.members(wrapped, SOURCES)))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/a.md:2: no such member `gone` on `a.Service`
                why: the member was renamed or removed, or is spelled differently
                fix: check the member against the code. Members, including those of supertypes of the repo: Service, api, field, inherited, run\
            """);
    assertThat(messages(DocChecks.members(wrapped, SOURCES)))
        .isEqualTo(messages(DocChecks.members(unwrapped, SOURCES)));
  }

  @Test
  void aConstructorOfAnInterfaceIsReported() {
    final Doc doc = doc("one/docs/a.md", "`Api#Api`");

    assertThat(messages(DocChecks.members(doc, SOURCES)))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/a.md:1: no matching constructor for `Api#Api`
                why: the type of this name is an interface, and an interface has no constructor
                fix: check the kind of the type in the code: a.Api\
            """);
  }

  @Test
  void anAmbiguousNameIsCheckedAgainstAllCandidatesWithAWarning() {
    final Doc doc = doc("one/docs/a.md", "`Dup#first` `b.Dup#second`");

    assertThat(DocChecks.members(doc, SOURCES))
        .containsExactly(
            new Warning(
                Path.of("connectors/agentic-ai/one/docs/a.md"),
                1,
                "name `Dup#first` is ambiguous and was checked against all types of that name:"
                    + " a.Dup, b.Dup",
                "the member exists on one of the types of that name, and the doc does not say which",
                "write the qualified name to pin one of them"));
  }

  @Test
  void anAmbiguousNameWithoutTheMemberListsAllCandidates() {
    final Doc doc = doc("one/docs/a.md", "`Dup#third`");

    assertThat(messages(DocChecks.members(doc, SOURCES)))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/a.md:1: no type named Dup has the member of `Dup#third`
                why: the member was renamed or removed, or is spelled differently
                fix: check the member against the code. The types named Dup are: a.Dup, b.Dup. Write the qualified name to pin one of them. Members, including those of supertypes of the repo: first, second\
            """);
  }

  @Test
  void aQualifiedNameThatPinsATypeIsCheckedOnlyAgainstIt() {
    final Doc doc = doc("one/docs/a.md", "`a.Dup#second` `c.Dup#first`");

    assertThat(messages(DocChecks.members(doc, SOURCES)))
        .containsExactly(
            """
            connectors/agentic-ai/one/docs/a.md:1: no such member `second` on `a.Dup`
                why: the member was renamed or removed, or is spelled differently
                fix: check the member against the code. Members, including those of supertypes of the repo: first\
            """,
            """
            connectors/agentic-ai/one/docs/a.md:1: qualified name `c.Dup` of `c.Dup#first` not found
                why: no type of the repo sources has this qualified name
                fix: check the package and outer types. The types named Dup are: a.Dup, b.Dup\
            """);
  }

  @Test
  void aTypeOfADependencyIsOnlyWarnedAbout() {
    final Doc doc = doc("one/docs/a.md", "`External#timeout`");

    assertThat(DocChecks.members(doc, SOURCES))
        .containsExactly(
            new Warning(
                Path.of("connectors/agentic-ai/one/docs/a.md"),
                1,
                "member of `External#timeout` not verified: `External` is not declared in the repo"
                    + " sources, so it is a type of a dependency whose classes are not loaded",
                "the check reads the code of the repo, so it cannot see the members of a dependency",
                "check the member against the documentation or the sources of the dependency"));
  }

  @Test
  void aWarningIsLoggedAsOneLineAndReportedWithWhyAndFix() {
    final Path file = Path.of("connectors/agentic-ai/one/docs/a.md");
    final Warning warning = new Warning(file, 7, "what", "why", "fix");
    final Warning withoutLine = new Warning(file, 0, "what", "why", "fix");

    assertThat(List.of(warning.logLine(), withoutLine.logLine()))
        .containsExactly(
            "WARN connectors/agentic-ai/one/docs/a.md:7: what",
            "WARN connectors/agentic-ai/one/docs/a.md: what");
    assertThat(List.of(warning.toString(), withoutLine.toString()))
        .containsExactly(
            "connectors/agentic-ai/one/docs/a.md:7: what\n    why: why\n    fix: fix",
            "connectors/agentic-ai/one/docs/a.md: what\n    why: why\n    fix: fix");
  }

  @Test
  void allowedNamesMustBeUsedAndUnknownToTheCode() {
    final Map<String, String> allowed = Map.of("Used", "r", "Unused", "r", "Service", "r");

    assertThat(
            messages(
                DocChecks.allowedNames(
                    allowed, Set.of("Used", "Service"), SOURCES, Path.of("Test.java"))))
        .containsExactly(
            """
            Test.java: allowed name `Service` is now declared by the code
                why: the code is the source of truth, and the check of the doc verifies a declared name against it
                fix: delete the entry of the allowed names\
            """,
            """
            Test.java: allowed name `Unused` is used in no doc
                why: an entry that no doc needs keeps a name known that nothing uses
                fix: delete the entry of the allowed names\
            """);
    assertThat(
            DocChecks.allowedNames(
                Map.of("Used", "r"), Set.of("Used"), SOURCES, Path.of("Test.java")))
        .isEmpty();
  }

  @Test
  void collectsTheTypesNamedInTheDocs() {
    assertThat(
            DocChecks.namedTypes(
                Stream.of(doc("a.md", "`Foo` `Bar#baz`"), doc("b.md", "`List<Qux>` `plain`"))))
        .containsExactly("Bar", "Foo", "List", "Qux");
  }

  private static List<String> messages(final java.util.stream.Stream<Finding> findings) {
    return findings.map(Object::toString).toList();
  }

  private static Doc doc(final String path, final String... lines) {
    return MarkdownParser.parse(
        Path.of("connectors/agentic-ai").resolve(path), String.join("\n", lines) + "\n");
  }

  private static String readme(
      final String title, final String docsEntry, final String blank, final String last) {
    return String.join(
        "\n",
        title,
        "",
        "## Purpose",
        "",
        "## Key types",
        "",
        "## Docs",
        "",
        docsEntry,
        blank,
        last,
        "");
  }

  private void write(final String path, final String content) throws IOException {
    final Path file = root.resolve("connectors/agentic-ai").resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
