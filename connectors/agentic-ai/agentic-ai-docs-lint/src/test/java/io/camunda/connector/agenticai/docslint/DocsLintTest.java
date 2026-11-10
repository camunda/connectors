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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocsLintTest {

  private static final DocModule MODULE = new DocModule("one", Path.of("project/one"));

  private static final Map<String, String> SOURCES =
      Map.of(
          "lib/a/Base.java",
          "package a; class Base { void inherited() {} }",
          "lib/a/Service.java",
          "package a; import java.util.List; import java.util.Map; import java.util.Optional;"
              + " import b.External;"
              + " class Service extends Base implements Api {"
              + " Service(int x) {} void run() {} int field; record Nested(String part) {} }",
          "lib/a/Api.java",
          "package a; interface Api { void api(); }",
          "lib/a/Dup.java",
          "package a; class Dup { void first() {} }",
          "lib/b/Dup.java",
          "package b; class Dup { void second() {} }");

  private static final Map<String, String> INHERITANCE =
      Map.of(
          "lib/c/Failure.java",
          "package c; class Failure extends RuntimeException { void run() {} }",
          "lib/c/Plain.java",
          "package c; class Plain { void run() {} }",
          "lib/c/Point.java",
          "package c; record Point(int x) {}",
          "lib/c/Mode.java",
          "package c; enum Mode { FIRST }",
          "lib/c/Client.java",
          "package c; import lib.Vendor; class Client extends Vendor { void own() {} }");

  @TempDir Path root;

  @BeforeEach
  void writeASourceWithoutTypes() throws IOException {
    write("lib/package-info.java", "package lib;");
  }

  @Test
  void aReadmeWithTheLayoutAndLinkedDocsHasNoFindings() throws IOException {
    write(
        "project/one/README.md",
        "# one\n\n## Purpose\n\n## Key types\n\n## Docs\n\n- [Doc](docs/a.md)\n\n## More\n");
    write("project/one/docs/a.md", "# a");

    assertThat(lint().findReadmeLayoutViolations(MODULE).all()).isEmpty();
  }

  @Test
  void aMissingReadmeIsReportedWithTheTitleOfTheModule() {
    assertThat(messages(lint().findReadmeLayoutViolations(MODULE)))
        .containsExactly(
            """
            project/one/README.md: README is missing
                why: a reader starts at the README of a module, and it links the docs of the module
                fix: add a README.md that starts with `# one` and has the sections [## Purpose, ## Key types, ## Docs]\
            """);
    assertThat(
            messages(
                lint()
                    .findReadmeLayoutViolations(
                        new DocModule("titled", Path.of("project/titled")))))
        .containsExactly(
            """
            project/titled/README.md: README is missing
                why: a reader starts at the README of a module, and it links the docs of the module
                fix: add a README.md that starts with `# Titled Module` and has the sections [## Purpose, ## Key types, ## Docs]\
            """);
  }

  @Test
  void aWrongTitleIsReportedAndAnOverrideIsAccepted() throws IOException {
    write("project/one/README.md", "# other\n\n## Purpose\n\n## Key types\n\n## Docs\n");
    write("project/titled/README.md", "# Titled Module\n\n## Purpose\n\n## Key types\n\n## Docs\n");

    assertThat(messages(lint().findReadmeLayoutViolations(MODULE)))
        .containsExactly(
            """
            project/one/README.md:1: first heading is `# other`, expected `# one`
                why: the first heading names the module, so a reader and a search find the README by it
                fix: start the README with `# one`\
            """);
    assertThat(
            lint()
                .findReadmeLayoutViolations(new DocModule("titled", Path.of("project/titled")))
                .all())
        .isEmpty();
  }

  @Test
  void wrongSectionsAreReported() throws IOException {
    write("project/one/README.md", "# one\n\n## Purpose\n\n## Docs\n\n## Key types\n");

    assertThat(messages(lint().findReadmeLayoutViolations(MODULE)))
        .containsExactly(
            """
            project/one/README.md:3: the first sections are [## Purpose, ## Docs, ## Key types], expected [## Purpose, ## Key types, ## Docs]
                why: all module READMEs share one layout, so a reader finds the same section in each
                fix: put the sections in this order before any other `##` section\
            """);
  }

  @Test
  void aDocThatTheReadmeDoesNotLinkUnderDocsIsReported() throws IOException {
    write(
        "project/one/README.md",
        "# one\n\n## Purpose\n\n[Doc](docs/a.md)\n\n## Key types\n\n## Docs\n\n- [B](docs/b.md)\n\n## More\n\n[C](docs/c.md)\n");
    write("project/one/docs/a.md", "# a");
    write("project/one/docs/b.md", "# b");
    write("project/one/docs/c.md", "# c");

    assertThat(messages(lint().findReadmeLayoutViolations(MODULE)))
        .containsExactly(
            """
            project/one/README.md:9: `## Docs` does not link `project/one/docs/a.md`
                why: the README lists every doc of the module, so a reader can find it
                fix: add a link to the doc under `## Docs`\
            """,
            """
            project/one/README.md:9: `## Docs` does not link `project/one/docs/c.md`
                why: the README lists every doc of the module, so a reader can find it
                fix: add a link to the doc under `## Docs`\
            """);
  }

  @Test
  void aDefinitionUnderDocsWithoutALinkDoesNotCountAsALink() throws IOException {
    write(
        "project/one/README.md",
        "# one\n\n## Purpose\n\n## Key types\n\n## Docs\n\n[topic]: docs/a.md\n");
    write("project/one/docs/a.md", "# a");

    assertThat(messages(lint().findReadmeLayoutViolations(MODULE)))
        .containsExactly(
            """
            project/one/README.md:7: `## Docs` does not link `project/one/docs/a.md`
                why: the README lists every doc of the module, so a reader can find it
                fix: add a link to the doc under `## Docs`\
            """);
  }

  @Test
  void aReferenceLinkUnderDocsCountsWhereverItsDefinitionIs() throws IOException {
    write(
        "project/one/README.md",
        "# one\n\n## Purpose\n\n## Key types\n\n## Docs\n\n- [A][topic]\n\n## More\n\n[topic]: docs/a.md\n");
    write("project/one/docs/a.md", "# a");

    assertThat(lint().findReadmeLayoutViolations(MODULE).all()).isEmpty();
  }

  @Test
  void aReferenceLinkOutsideDocsDoesNotCountEvenWithItsDefinitionUnderDocs() throws IOException {
    write(
        "project/one/README.md",
        "# one\n\n## Purpose\n\n[A][topic]\n\n## Key types\n\n## Docs\n\n[topic]: docs/a.md\n");
    write("project/one/docs/a.md", "# a");

    assertThat(messages(lint().findReadmeLayoutViolations(MODULE)))
        .containsExactly(
            """
            project/one/README.md:9: `## Docs` does not link `project/one/docs/a.md`
                why: the README lists every doc of the module, so a reader can find it
                fix: add a link to the doc under `## Docs`\
            """);
  }

  @Test
  void aBrokenDefinitionIsReported() throws IOException {
    assertThat(
            messages(findStaleReferences("project/one/docs/source.md", "# Source", "[a]: gone.md")))
        .containsExactly(
            """
            project/one/docs/source.md:2: link `gone.md` points to a file that does not exist (resolved to `project/one/docs/gone.md`)
                why: the file was renamed, moved or deleted, or the path is wrong
                fix: check the path against the file tree\
            """);
  }

  @Test
  void aSchemeRelativeLinkIsExternalAndARootRelativeLinkIsNot() throws IOException {
    assertThat(
            messages(
                findStaleReferences(
                    "project/one/docs/source.md",
                    "# Source",
                    "[a](//example.com/guide.md) [b](/guide.md)")))
        .containsExactly(
            """
            project/one/docs/source.md:2: link `/guide.md` points to a file that does not exist (resolved to `guide.md`)
                why: the file was renamed, moved or deleted, or the path is wrong
                fix: check the path against the file tree\
            """);
  }

  @Test
  void relativeLinksThatResolveHaveNoFindings() throws IOException {
    write("project/one/docs/target.md", "# Target\n## Section\n");
    write("project/one/docs/a b+c.md", "# Spaced");
    write("top.md", "# Top");

    assertThat(
            findStaleReferences(
                "project/one/docs/source.md",
                "# Source",
                "[a](target.md#section) [b](a%20b%2Bc.md) [c](/top.md) [d](#source)",
                "[e](target.md?plain=1#L5) [f](../docs) [g](https://example.com/missing)"))
        .isEmpty();
  }

  @Test
  void aMissingFileIsReported() throws IOException {
    assertThat(
            messages(findStaleReferences("project/one/docs/source.md", "# Source", "[a](gone.md)")))
        .containsExactly(
            """
            project/one/docs/source.md:2: link `gone.md` points to a file that does not exist (resolved to `project/one/docs/gone.md`)
                why: the file was renamed, moved or deleted, or the path is wrong
                fix: check the path against the file tree\
            """);
  }

  @Test
  void aBrokenAnchorListsTheAnchorsOfTheTarget() throws IOException {
    write("project/one/docs/target.md", "# Target\n## Section\n");

    assertThat(
            messages(
                findStaleReferences(
                    "project/one/docs/source.md", "# Source", "[a](target.md#nope) [b](#nope)")))
        .containsExactly(
            """
            project/one/docs/source.md:2: link `target.md#nope` points to an anchor that does not exist in `project/one/docs/target.md`
                why: the heading was renamed or removed, or GitHub derives a different anchor from it
                fix: check the headings of the target. Its anchors are: target, section\
            """,
            """
            project/one/docs/source.md:2: link `#nope` points to an anchor that does not exist in `project/one/docs/source.md`
                why: the heading was renamed or removed, or GitHub derives a different anchor from it
                fix: check the headings of the target. Its anchors are: source\
            """);
  }

  @Test
  void aBadEscapeIsReported() throws IOException {
    assertThat(
            messages(findStaleReferences("project/one/docs/source.md", "# Source", "[a](%zz.md)")))
        .containsExactly(
            """
            project/one/docs/source.md:2: link `%zz.md` is not a valid URI: Malformed escape pair
                why: the path cannot be decoded, so it names no file
                fix: percent-encode the character (a literal % is written %25)\
            """);
  }

  @Test
  void aLinkWithCharactersThatAUriForbidsResolvesToTheFile() throws IOException {
    write("project/one/docs/a[b].md", "# Brackets");
    write("project/one/docs/{c}|d.md", "# Others");

    assertThat(
            findStaleReferences(
                "project/one/docs/source.md", "# Source", "[a](a[b].md) [b](<{c}|d.md>)"))
        .isEmpty();
  }

  @Test
  void aLinkWithBracketsToAMissingFileNamesTheResolvedFile() throws IOException {
    assertThat(
            messages(findStaleReferences("project/one/docs/source.md", "# Source", "[a](a[b].md)")))
        .containsExactly(
            """
            project/one/docs/source.md:2: link `a[b].md` points to a file that does not exist (resolved to `project/one/docs/a[b].md`)
                why: the file was renamed, moved or deleted, or the path is wrong
                fix: check the path against the file tree\
            """);
  }

  @Test
  void aLinkThatLeavesTheRepositoryIsReported() throws IOException {
    assertThat(
            messages(
                findStaleReferences(
                    "project/one/docs/source.md", "# Source", "[a](../../../../outside.md)")))
        .containsExactly(
            """
            project/one/docs/source.md:2: link `../../../../outside.md` leaves the repository
                why: the target is outside the repository, so a reader of a checkout cannot open it
                fix: link a file inside the repository\
            """);
  }

  @Test
  void aPlainViewLinkHasNoAnchorCheck() throws IOException {
    write("project/one/docs/target.md", "# Target");

    assertThat(
            messages(
                findStaleReferences(
                    "project/one/docs/source.md",
                    "# Source",
                    "[a](target.md?plain=1#L5) [b](target.md#L5)")))
        .containsExactly(
            """
            project/one/docs/source.md:2: link `target.md#L5` points to an anchor that does not exist in `project/one/docs/target.md`
                why: the heading was renamed or removed, or GitHub derives a different anchor from it
                fix: check the headings of the target. Its anchors are: target\
            """);
  }

  @Test
  void aQueryOtherThanPlainViewKeepsTheAnchorCheck() throws IOException {
    write("project/one/docs/target.md", "# Target\n## Existing\n");

    assertThat(
            messages(
                findStaleReferences(
                    "project/one/docs/source.md", "# Source", "[a](target.md?foo=bar#missing)")))
        .containsExactly(
            """
            project/one/docs/source.md:2: link `target.md?foo=bar#missing` points to an anchor that does not exist in `project/one/docs/target.md`
                why: the heading was renamed or removed, or GitHub derives a different anchor from it
                fix: check the headings of the target. Its anchors are: target, existing\
            """);
  }

  @Test
  void aQueryOtherThanPlainViewPassesWithAnExistingAnchor() throws IOException {
    write("project/one/docs/target.md", "# Target\n## Existing\n");

    assertThat(
            findStaleReferences(
                "project/one/docs/source.md", "# Source", "[a](target.md?foo=bar#existing)"))
        .isEmpty();
  }

  @Test
  void aPlainViewAmongOtherParametersHasNoAnchorCheck() throws IOException {
    write("project/one/docs/target.md", "# Target");

    assertThat(
            findStaleReferences(
                "project/one/docs/source.md", "# Source", "[a](target.md?plain=1&foo=bar#L5)"))
        .isEmpty();
  }

  @Test
  void aPlainParameterThatIsNotOneKeepsTheAnchorCheck() throws IOException {
    write("project/one/docs/target.md", "# Target");

    assertThat(
            messages(
                findStaleReferences(
                    "project/one/docs/source.md", "# Source", "[a](target.md?plain=0#missing)")))
        .containsExactly(
            """
            project/one/docs/source.md:2: link `target.md?plain=0#missing` points to an anchor that does not exist in `project/one/docs/target.md`
                why: the heading was renamed or removed, or GitHub derives a different anchor from it
                fix: check the headings of the target. Its anchors are: target\
            """);
  }

  @Test
  void anEncodedFragmentMatchesTheDecodedAnchor() throws IOException {
    write("project/one/docs/target.md", "# Target\n## Größe\n");

    assertThat(
            findStaleReferences(
                "project/one/docs/source.md", "# Source", "[a](target.md#gr%C3%B6%C3%9Fe)"))
        .isEmpty();
  }

  @Test
  void anEncodedFragmentThatMatchesNoAnchorIsReported() throws IOException {
    write("project/one/docs/target.md", "# Target\n## Größe\n");

    assertThat(
            messages(
                findStaleReferences(
                    "project/one/docs/source.md", "# Source", "[a](target.md#gr%C3%BCsse)")))
        .containsExactly(
            """
            project/one/docs/source.md:2: link `target.md#gr%C3%BCsse` points to an anchor that does not exist in `project/one/docs/target.md`
                why: the heading was renamed or removed, or GitHub derives a different anchor from it
                fix: check the headings of the target. Its anchors are: target, größe\
            """);
  }

  @Test
  void aPlainViewLineAnchorHasNoFindings() throws IOException {
    write("project/one/docs/target.md", "# Target");

    assertThat(
            findStaleReferences(
                "project/one/docs/source.md", "# Source", "[a](target.md?plain=1#L5)"))
        .isEmpty();
  }

  @Test
  void aLinkToADirectoryWithATrailingSlashHasNoFindings() throws IOException {
    write("project/one/docs/target.md", "# Target");

    assertThat(findStaleReferences("project/one/docs/source.md", "# Source", "[a](../docs/)"))
        .isEmpty();
  }

  @Test
  void aFragmentOnlyLinkIsCheckedAgainstTheAnchorsOfTheDoc() throws IOException {
    assertThat(
            messages(
                findStaleReferences(
                    "project/one/docs/source.md", "# Source", "[a](#source) [b](#nope)")))
        .containsExactly(
            """
            project/one/docs/source.md:2: link `#nope` points to an anchor that does not exist in `project/one/docs/source.md`
                why: the heading was renamed or removed, or GitHub derives a different anchor from it
                fix: check the headings of the target. Its anchors are: source\
            """);
  }

  @Test
  void aDestinationWithSpacesInAngleBracketsResolvesToTheFile() throws IOException {
    write("project/one/docs/c d.md", "# Spaced\n## Part\n");

    assertThat(
            findStaleReferences(
                "project/one/docs/source.md", "# Source", "[a](<c d.md#part>) [b](<c d.md>)"))
        .isEmpty();
  }

  @Test
  void aPlusInTheLinkStaysAPlusInTheFileName() throws IOException {
    write("project/one/docs/a+b.md", "# Plus");

    assertThat(findStaleReferences("project/one/docs/source.md", "# Source", "[a](a+b.md)"))
        .isEmpty();
  }

  @Test
  void aJavaFenceIsReportedInAQuoteToo() throws IOException {
    assertThat(
            messages(
                findStaleReferences(
                    "project/one/docs/a.md",
                    "# A",
                    "```java",
                    "int x;",
                    "```",
                    "> ```Java",
                    "> int y;",
                    "> ```",
                    "```json",
                    "```")))
        .containsExactly(
            """
            project/one/docs/a.md:2: Java code block
                why: a code excerpt goes stale when the code changes
                fix: replace the excerpt with the name of the type and member, for example `Type#member`\
            """,
            """
            project/one/docs/a.md:5: Java code block
                why: a code excerpt goes stale when the code changes
                fix: replace the excerpt with the name of the type and member, for example `Type#member`\
            """);
  }

  @Test
  void namesOfTheCodeAreKnownAndOthersAreReportedWithSimilarNames() throws IOException {
    writeSources(SOURCES);

    assertThat(
            messages(
                findStaleReferences(
                    lint(Map.of("Allowed", "reason")),
                    "project/one/docs/a.md",
                    "`Service`, `Map<String, Service>`, `a.Api`, `Servise`, `Allowed`")))
        .containsExactly(
            """
            project/one/docs/a.md:1: unknown type `Servise` in `Servise`
                why: the code declares, imports or nests no type of this name, so the doc may name a type that was renamed or removed
                fix: check the name against the code. Similar names in the code (they may be unrelated): Service\
            """);
  }

  @Test
  void similarNamesAreTheFiveClosestWithTiesInAlphabeticalOrder() throws IOException {
    writeSources(
        Map.of(
            "lib/p/Names.java",
            "package p; class AAWidget {} class ABWidget {} class ACWidget {}"
                + " class BWidget {} class CWidget {} class Widgets {} class Unrelated {}"));

    assertThat(messages(findStaleReferences("project/one/docs/a.md", "`Widget`")))
        .containsExactly(
            """
            project/one/docs/a.md:1: unknown type `Widget` in `Widget`
                why: the code declares, imports or nests no type of this name, so the doc may name a type that was renamed or removed
                fix: check the name against the code. Similar names in the code (they may be unrelated): BWidget, CWidget, Widgets, AAWidget, ABWidget\
            """);
  }

  @Test
  void genericArgumentsAreChecked() throws IOException {
    writeSources(SOURCES);

    assertThat(
            messages(
                findStaleReferences(
                    "project/one/docs/a.md", "`List<Missing>` and `Optional<? extends Service>`")))
        .containsExactly(
            """
            project/one/docs/a.md:1: unknown type `Missing` in `List<Missing>`
                why: the code declares, imports or nests no type of this name, so the doc may name a type that was renamed or removed
                fix: check the name against the code\
            """);
  }

  @Test
  void typeNamesFollowTheConventions() throws IOException {
    assertThat(unknownTypes("Foo<Alpha>.Bar<Beta>")).containsExactly("Foo", "Bar", "Alpha", "Beta");
    assertThat(unknownTypes("@Foo(x = 1)")).containsExactly("Foo");
    assertThat(unknownTypes("io.camunda.Foo.Bar#baz")).containsExactly("Foo", "Bar");
    assertThat(unknownTypes("Foo.java")).containsExactly("Foo");
    assertThat(unknownTypes("Foo[]")).containsExactly("Foo");
    assertThat(unknownTypes("List<Foo> and Map<Bar>")).isEmpty();
    assertThat(unknownTypes("MAX_TOKENS")).isEmpty();
  }

  @Test
  void memberReferencesResolveWithInheritedMembersAndConstructors() throws IOException {
    writeSources(SOURCES);

    assertThat(
            findStaleReferences(
                "project/one/docs/a.md",
                "`Service#run` `Service#field()` `Service#Service(int)` `Service#inherited`",
                "`Service#api` `Service.Nested#part` `a.Service#run` `Nested#part` `Api#api`"))
        .isEmpty();
  }

  @Test
  void aMissingMemberListsTheMembers() throws IOException {
    writeSources(SOURCES);

    assertThat(messages(findStaleReferences("project/one/docs/a.md", "# A", "`Service#gone()`")))
        .containsExactly(
            """
            project/one/docs/a.md:2: no such member `gone` on `a.Service`
                why: the member was renamed or removed, or is spelled differently
                fix: check the member against the code. Members, including those of supertypes of the repo: Service, api, field, inherited, run\
            """);
  }

  @Test
  void aMemberInheritedFromAJdkTypeResolves() throws IOException {
    writeSources(INHERITANCE);

    assertThat(
            findStaleReferences(
                "project/one/docs/a.md",
                "`Failure#getCause` `Failure#getMessage()` `Failure#run` `Plain#toString`",
                "`Plain#equals` `Plain#hashCode` `Plain#getClass` `Client#own` `Client#toString`"))
        .isEmpty();
  }

  @Test
  void theGeneratedMembersOfARecordResolve() throws IOException {
    writeSources(INHERITANCE);

    assertThat(
            findStaleReferences(
                "project/one/docs/a.md",
                "`Point#toString` `Point#equals` `Point#hashCode` `Point#x`"))
        .isEmpty();
  }

  @Test
  void theGeneratedMembersOfAnEnumResolve() throws IOException {
    writeSources(INHERITANCE);

    assertThat(
            findStaleReferences(
                "project/one/docs/a.md",
                "`Mode#values` `Mode#valueOf` `Mode#name` `Mode#ordinal` `Mode#FIRST`"))
        .isEmpty();
  }

  @Test
  void aMissingMemberOfATypeWithAnExternalSupertypeIsOnlyWarnedAbout() throws IOException {
    writeSources(INHERITANCE);

    assertThat(findStaleReferences("project/one/docs/a.md", "`Client#missing`"))
        .containsExactly(
            new Warning(
                Path.of("project/one/docs/a.md"),
                1,
                "member of `Client#missing` not verified",
                "c.Client extends Vendor, an external dependency type that is not loaded, so the"
                    + " member may be inherited",
                "name the declaring type or check the member in the library"));
  }

  @Test
  void aMissingMemberOfATypeWithOnlyRepoAndJdkSupertypesIsReported() throws IOException {
    writeSources(INHERITANCE);

    assertThat(messages(findStaleReferences("project/one/docs/a.md", "# A", "`Failure#gone`")))
        .containsExactly(
            """
            project/one/docs/a.md:2: no such member `gone` on `c.Failure`
                why: the member was renamed or removed, or is spelled differently
                fix: check the member against the code. Members, including those of supertypes of the repo: run\
            """);
  }

  @Test
  void aWrappedReferenceToAnExistingMemberHasNoFindings() throws IOException {
    writeSources(SOURCES);

    assertThat(
            findStaleReferences(
                "project/one/docs/a.md", "`Service", "#run` `Service#", "run()` `Service  #  run`"))
        .isEmpty();
  }

  @Test
  void aWrappedReferenceHasItsTypeNameChecked() throws IOException {
    writeSources(SOURCES);

    assertThat(
            messages(
                findStaleReferences(
                    "project/one/docs/a.md", "`Service", "#run`", "`Missing", "#run`")))
        .containsExactly(
            """
            project/one/docs/a.md:3: unknown type `Missing` in `Missing #run`
                why: the code declares, imports or nests no type of this name, so the doc may name a type that was renamed or removed
                fix: check the name against the code\
            """,
            """
            project/one/docs/a.md:3: member of `Missing#run` not verified: `Missing` is not declared in the repo sources, so it is a type of a dependency whose classes are not loaded
                why: the check reads the code of the repo, so it cannot see the members of a dependency
                fix: check the member against the documentation or the sources of the dependency\
            """);
  }

  @Test
  void aWrappedReferenceToAMissingMemberIsReportedLikeTheUnwrappedForm() throws IOException {
    writeSources(SOURCES);

    final List<String> wrapped =
        messages(findStaleReferences("project/one/docs/a.md", "# A", "`Service", "#gone()`"));

    assertThat(wrapped)
        .containsExactly(
            """
            project/one/docs/a.md:2: no such member `gone` on `a.Service`
                why: the member was renamed or removed, or is spelled differently
                fix: check the member against the code. Members, including those of supertypes of the repo: Service, api, field, inherited, run\
            """);
    assertThat(wrapped)
        .isEqualTo(
            messages(findStaleReferences("project/one/docs/a.md", "# A", "`Service#gone()`")));
  }

  @Test
  void aConstructorOfAnInterfaceIsReported() throws IOException {
    writeSources(SOURCES);

    assertThat(messages(findStaleReferences("project/one/docs/a.md", "`Api#Api`")))
        .containsExactly(
            """
            project/one/docs/a.md:1: no matching constructor for `Api#Api`
                why: the type of this name is an interface, and an interface has no constructor
                fix: check the kind of the type in the code: a.Api\
            """);
  }

  @Test
  void anAmbiguousNameIsCheckedAgainstAllCandidatesWithAWarning() throws IOException {
    writeSources(SOURCES);

    assertThat(findStaleReferences("project/one/docs/a.md", "`Dup#first` `b.Dup#second`"))
        .containsExactly(
            new Warning(
                Path.of("project/one/docs/a.md"),
                1,
                "name `Dup#first` is ambiguous and was checked against all types of that name:"
                    + " a.Dup, b.Dup",
                "the member exists on one of the types of that name, and the doc does not say which",
                "write the qualified name to pin one of them"));
  }

  @Test
  void anAmbiguousNameWithoutTheMemberListsAllCandidates() throws IOException {
    writeSources(SOURCES);

    assertThat(messages(findStaleReferences("project/one/docs/a.md", "`Dup#third`")))
        .containsExactly(
            """
            project/one/docs/a.md:1: no type named Dup has the member of `Dup#third`
                why: the member was renamed or removed, or is spelled differently
                fix: check the member against the code. The types named Dup are: a.Dup, b.Dup. Write the qualified name to pin one of them. Members, including those of supertypes of the repo: first, second\
            """);
  }

  @Test
  void aQualifiedNameThatPinsATypeIsCheckedOnlyAgainstIt() throws IOException {
    writeSources(SOURCES);

    assertThat(
            messages(findStaleReferences("project/one/docs/a.md", "`a.Dup#second` `c.Dup#first`")))
        .containsExactly(
            """
            project/one/docs/a.md:1: no such member `second` on `a.Dup`
                why: the member was renamed or removed, or is spelled differently
                fix: check the member against the code. Members, including those of supertypes of the repo: first\
            """,
            """
            project/one/docs/a.md:1: qualified name `c.Dup` of `c.Dup#first` not found
                why: no type of the repo sources has this qualified name
                fix: check the package and outer types. The types named Dup are: a.Dup, b.Dup\
            """);
  }

  @Test
  void aTypeOfADependencyIsOnlyWarnedAbout() throws IOException {
    writeSources(SOURCES);

    assertThat(findStaleReferences("project/one/docs/a.md", "`External#timeout`"))
        .containsExactly(
            new Warning(
                Path.of("project/one/docs/a.md"),
                1,
                "member of `External#timeout` not verified: `External` is not declared in the repo"
                    + " sources, so it is a type of a dependency whose classes are not loaded",
                "the check reads the code of the repo, so it cannot see the members of a dependency",
                "check the member against the documentation or the sources of the dependency"));
  }

  @Test
  void theJavaSourcesOfTheWholeRepositoryCountWithoutSkippedDirectories() throws IOException {
    writeSources(
        Map.of(
            "other/Sample.java",
            "package p; class Sample {}",
            "other/target/Generated.java",
            "package p; class Generated {}"));

    assertThat(messages(findStaleReferences("project/one/docs/a.md", "`Sample` `Generated`")))
        .containsExactly(
            """
            project/one/docs/a.md:1: unknown type `Generated` in `Generated`
                why: the code declares, imports or nests no type of this name, so the doc may name a type that was renamed or removed
                fix: check the name against the code\
            """);
  }

  @Test
  void aWarningIsLoggedAsOneLineAndReportedWithWhyAndFix() {
    final Path file = Path.of("project/one/docs/a.md");
    final Warning warning = new Warning(file, 7, "what", "why", "fix");
    final Warning withoutLine = new Warning(file, 0, "what", "why", "fix");

    assertThat(List.of(warning.logLine(), withoutLine.logLine()))
        .containsExactly("WARN project/one/docs/a.md:7: what", "WARN project/one/docs/a.md: what");
    assertThat(List.of(warning.toString(), withoutLine.toString()))
        .containsExactly(
            "project/one/docs/a.md:7: what\n    why: why\n    fix: fix",
            "project/one/docs/a.md: what\n    why: why\n    fix: fix");
  }

  @Test
  void theWarningsAreReportedAndTheViolationsMakeTheFailureMessage() {
    final Path file = Path.of("project/one/docs/a.md");
    final Warning warning = new Warning(file, 1, "warned", "why", "fix");
    final Findings findings =
        new Findings(
            List.of(
                new Violation(file, 1, "first", "why", "fix"),
                warning,
                new Violation(file, 2, "second", "why", "fix")));

    assertThat(findings.warnings()).containsExactly(warning);
    assertThat(findings.failureMessage())
        .contains(
            """

            project/one/docs/a.md:1: first
                why: why
                fix: fix
            project/one/docs/a.md:2: second
                why: why
                fix: fix\
            """);
    assertThat(new Findings(List.of(warning)).failureMessage()).isEmpty();
  }

  @Test
  void allowedNamesMustBeUsedInACurrentDocAndUnknownToTheCode() throws IOException {
    writeSources(SOURCES);
    write("project/one/docs/a.md", "`Used` `Service`");
    write("project/docs/history.md", "`Unused`");

    assertThat(
            messages(
                lint(Map.of("Used", "r", "Unused", "r", "Service", "r")).findStaleAllowedNames()))
        .containsExactly(
            """
            Policy.java: allowed name `Service` is now declared by the code
                why: the code is the source of truth, and the check of the doc verifies a declared name against it
                fix: delete the entry of the allowed names\
            """,
            """
            Policy.java: allowed name `Unused` is used in no doc
                why: an entry that no doc needs keeps a name known that nothing uses
                fix: delete the entry of the allowed names\
            """);
    assertThat(lint(Map.of("Used", "r")).findStaleAllowedNames().all()).isEmpty();
  }

  @Test
  void theTypesNamedInTheDocsCountAsUses() throws IOException {
    write("project/one/docs/a.md", "`Foo` `Bar#baz`");
    write("project/one/docs/b.md", "`List<Qux>` `plain`");

    assertThat(
            messages(
                lint(Map.of("Foo", "r", "Bar", "r", "List", "r", "Qux", "r", "plain", "r"))
                    .findStaleAllowedNames()))
        .containsExactly(
            """
            Policy.java: allowed name `plain` is used in no doc
                why: an entry that no doc needs keeps a name known that nothing uses
                fix: delete the entry of the allowed names\
            """);
  }

  @Test
  void aHistoryDocHasItsLinksAndFencesCheckedButNotItsNames() throws IOException {
    final String[] lines = {"# History", "[a](gone.md) `Gone` `Gone#member`", "```java", "```"};

    assertThat(messages(findStaleReferences("project/docs/history.md", lines)))
        .containsExactly(
            """
            project/docs/history.md:2: link `gone.md` points to a file that does not exist (resolved to `project/docs/gone.md`)
                why: the file was renamed, moved or deleted, or the path is wrong
                fix: check the path against the file tree\
            """,
            """
            project/docs/history.md:3: Java code block
                why: a code excerpt goes stale when the code changes
                fix: replace the excerpt with the name of the type and member, for example `Type#member`\
            """);
    assertThat(findStaleReferences("project/docs/current.md", lines))
        .extracting(DocsLintTest::what)
        .containsExactly(
            "link `gone.md` points to a file that does not exist (resolved to `project/docs/gone.md`)",
            "Java code block",
            "unknown type `Gone` in `Gone`",
            "unknown type `Gone` in `Gone#member`",
            "member of `Gone#member` not verified: `Gone` is not declared in the repo sources, so it"
                + " is a type of a dependency whose classes are not loaded");
  }

  @Test
  void listsTheDocsInScopeSortedAndWithoutExcludedDirectories() throws IOException {
    writeTree();

    assertThat(lint().docsInScope())
        .extracting(Path::toString)
        .containsExactly(
            "e2e/README.md",
            "project/README.md",
            "project/docs/history.md",
            "project/one/README.md");
  }

  @Test
  void doesNotFollowSymbolicLinks() throws IOException {
    writeTree();
    Files.createSymbolicLink(root.resolve("project/linked.md"), root.resolve("project/README.md"));

    assertThat(lint().docsInScope())
        .extracting(Path::toString)
        .containsExactly(
            "e2e/README.md",
            "project/README.md",
            "project/docs/history.md",
            "project/one/README.md");
  }

  @Test
  void readsTheModulesFromThePomAndAddsTheExtraModules() throws IOException {
    write(
        "project/pom.xml",
        "<project><modules><module>one</module><module>two</module></modules></project>");

    assertThat(lint().modulesInScope())
        .containsExactly(
            new DocModule("one", Path.of("project/one")),
            new DocModule("two", Path.of("project/two")),
            new DocModule("e2e", Path.of("e2e")));
  }

  @Test
  void readsOnlyTheDirectModulesOfThePom() throws IOException {
    write(
        "project/pom.xml",
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modules><module>one</module><module>two</module></modules>
          <profiles>
            <profile>
              <modules><module>profiled</module></modules>
            </profile>
          </profiles>
        </project>
        """);

    assertThat(lint().modulesInScope())
        .containsExactly(
            new DocModule("one", Path.of("project/one")),
            new DocModule("two", Path.of("project/two")),
            new DocModule("e2e", Path.of("e2e")));
  }

  private DocsLint lint() {
    return lint(Map.of());
  }

  private DocsLint lint(final Map<String, String> allowedNames) {
    return DocsLint.of(
        DocsLintConfigBuilder.builder()
            .root(root)
            .docRoots(List.of(Path.of("project"), Path.of("e2e")))
            .excludedDocs(
                List.of("project/docs/adr/**", "project/examples/**", "**/src/main/resources/**"))
            .historyDocs(Set.of(Path.of("project/docs/history.md")))
            .modulesPom(Path.of("project/pom.xml"))
            .extraModules(List.of(Path.of("e2e")))
            .readmeSections(List.of("## Purpose", "## Key types", "## Docs"))
            .readmeTitles(Map.of("titled", "# Titled Module"))
            .allowedNames(allowedNames)
            .allowedNamesSource(Path.of("Policy.java"))
            .build());
  }

  private List<Finding> findStaleReferences(final String path, final String... lines)
      throws IOException {
    return findStaleReferences(lint(), path, lines);
  }

  private List<Finding> findStaleReferences(
      final DocsLint lint, final String path, final String... lines) throws IOException {
    write(path, String.join("\n", lines) + "\n");
    return lint.findStaleReferences(Path.of(path)).all();
  }

  private List<String> unknownTypes(final String span) throws IOException {
    return findStaleReferences("project/one/docs/a.md", "`" + span + "`").stream()
        .map(DocsLintTest::what)
        .filter(what -> what.startsWith("unknown type `"))
        .map(what -> what.substring("unknown type `".length(), what.indexOf('`', 14)))
        .toList();
  }

  private static String what(final Finding finding) {
    return switch (finding) {
      case final Violation violation -> violation.what();
      case final Warning warning -> warning.what();
    };
  }

  private static List<String> messages(final Findings findings) {
    return messages(findings.all());
  }

  private static List<String> messages(final List<Finding> findings) {
    return findings.stream().map(Object::toString).toList();
  }

  private void writeTree() throws IOException {
    write("project/README.md", "# root");
    write("project/one/README.md", "# one");
    write("project/docs/history.md", "# history");
    write("project/docs/adr/001.md", "# adr");
    write("project/examples/e.md", "# example");
    write("project/one/src/main/resources/prompt.md", "# prompt");
    write("project/one/target/generated.md", "# build");
    write("project/.hidden/h.md", "# hidden");
    write("project/node_modules/n.md", "# node");
    write("e2e/README.md", "# e2e");
    write("other/README.md", "# other");
  }

  private void writeSources(final Map<String, String> sources) throws IOException {
    for (final Map.Entry<String, String> source : sources.entrySet()) {
      write(source.getKey(), source.getValue());
    }
  }

  private void write(final String path, final String content) throws IOException {
    final Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
