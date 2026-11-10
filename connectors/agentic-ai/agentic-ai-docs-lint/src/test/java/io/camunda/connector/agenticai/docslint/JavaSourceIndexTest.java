/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.docslint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class JavaSourceIndexTest {

  private static final String SOURCE =
      """
      package io.camunda.sample;

      import io.camunda.sample.api.Imported;

      /** Replaces the class JavadocName and Outer.JavadocNested. */
      class Sample {
        // record LineName, Outer.LineNested
        /* enum BlockName */
        String text = "interface TextName, Outer.TextNested";
        char quote = '"';
        record Declared(Outer.Nested nested) {}
      }
      """;

  @Test
  void knowsDeclaredImportedAndNestedTypes() {
    final JavaSourceIndex index = index("Sample.java", SOURCE);

    assertThat(
            Stream.of("Sample", "Imported", "Declared", "Nested", "String")
                .filter(index::knows)
                .toList())
        .containsExactly("Sample", "Imported", "Declared", "Nested", "String");
  }

  @Test
  void doesNotKnowTypesNamedOnlyInCommentsOrStrings() {
    final JavaSourceIndex index = index("Sample.java", SOURCE);

    assertThat(
            Stream.of(
                    "JavadocName",
                    "JavadocNested",
                    "LineName",
                    "LineNested",
                    "BlockName",
                    "TextName",
                    "TextNested")
                .filter(index::knows)
                .toList())
        .isEmpty();
  }

  @Test
  void nestedTypesHaveBinaryNamesAndSecondaryTopLevelTypesTheirOwn() {
    final JavaSourceIndex index =
        index(
            "Outer.java",
            """
            package io.camunda.sample;
            record Outer(@SuppressWarnings({"unused"}) int value) {
              static class Redirect {
                enum Type {}
              }
            }
            class Secondary {}
            """);

    assertThat(index.types("Type"))
        .extracting(JavaSourceIndex.Type::binaryName)
        .containsExactly("io.camunda.sample.Outer$Redirect$Type");
    assertThat(index.types("Secondary"))
        .extracting(JavaSourceIndex.Type::qualifiedName)
        .containsExactly("io.camunda.sample.Secondary");
    assertThat(index.types("Type").getFirst().simpleName()).isEqualTo("Type");
  }

  @Test
  void collectsMethodsFieldsConstructorsRecordComponentsAndEnumConstants() {
    final JavaSourceIndex index =
        index(
            "Members.java",
            """
            package p;
            class Plain {
              int field;
              Plain(int value) {}
              void method() {}
            }
            record Rec(String component) {
              Rec {}
              static Rec of() { return null; }
            }
            enum Mode { FIRST, SECOND }
            interface Contract { void call(); }
            """);

    assertThat(members(index, "Plain")).containsExactlyInAnyOrder("field", "Plain", "method");
    assertThat(members(index, "Rec")).containsExactlyInAnyOrder("component", "Rec", "of");
    assertThat(members(index, "Mode")).containsExactlyInAnyOrder("FIRST", "SECOND");
    assertThat(members(index, "Contract")).containsExactly("call");
    assertThat(index.types("Contract").getFirst().isInterface()).isTrue();
    assertThat(index.types("Plain").getFirst().isInterface()).isFalse();
  }

  @Test
  void localVariablesAndAnonymousClassesAddNoMembers() {
    final JavaSourceIndex index =
        index(
            "Local.java",
            """
            package p;
            class Local {
              void method(int parameter) {
                int variable = 0;
                Runnable runnable = new Runnable() { public void run() { int inner; } };
              }
            }
            """);

    assertThat(members(index, "Local")).containsExactly("method");
  }

  @Test
  void inheritsMembersFromSupertypesOfTheSourcesByTheirSimpleNames() {
    final JavaSourceIndex index =
        index(
            "Inheritance.java",
            """
            package p;
            class Base<T> { void inherited() {} }
            interface Api { void api(); }
            class Child extends Base<String> implements Api, java.io.Serializable { void own() {} }
            class Loop extends Loop {}
            """);

    assertThat(index.members(index.types("Child").getFirst()))
        .containsExactlyInAnyOrder("inherited", "api", "own");
    assertThat(index.members(index.types("Loop").getFirst())).isEmpty();
  }

  @Test
  void sameSimpleNameInSeveralPackagesGivesSeveralTypes() {
    final JavaSourceIndex index =
        JavaSourceIndex.of(
            Map.of(
                "a/Dup.java", "package a; class Dup {}", "b/Dup.java", "package b; class Dup {}"));

    assertThat(index.types("Dup"))
        .extracting(JavaSourceIndex.Type::qualifiedName)
        .containsExactlyInAnyOrder("a.Dup", "b.Dup");
  }

  @Test
  void aSyntaxErrorFailsWithTheFileAndTheLine() {
    assertThatThrownBy(() -> index("Broken.java", "package p;\nclass Broken {\n  int = ;\n}\n"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            """
            Java parse errors:
            Broken.java:3: <identifier> expected
            Broken.java:3: illegal start of expression\
            """);
  }

  @Test
  void anErrorWithoutASourceGivesOnlyTheMessage() {
    final Diagnostic<JavaFileObject> diagnostic =
        new Diagnostic<>() {
          @Override
          public Kind getKind() {
            return Kind.ERROR;
          }

          @Override
          public @Nullable JavaFileObject getSource() {
            return null;
          }

          @Override
          public long getPosition() {
            return NOPOS;
          }

          @Override
          public long getStartPosition() {
            return NOPOS;
          }

          @Override
          public long getEndPosition() {
            return NOPOS;
          }

          @Override
          public long getLineNumber() {
            return NOPOS;
          }

          @Override
          public long getColumnNumber() {
            return NOPOS;
          }

          @Override
          public @Nullable String getCode() {
            return null;
          }

          @Override
          public String getMessage(final @Nullable Locale locale) {
            return "no source files";
          }
        };

    assertThat(JavaSourceIndex.error(diagnostic)).isEqualTo("no source files");
  }

  @Test
  void callersCannotChangeTheIndexOrItsTypes() {
    final JavaSourceIndex index = index("Plain.java", "package p; class Plain { int field; }");
    final JavaSourceIndex.Type type = index.types("Plain").getFirst();

    assertThatThrownBy(() -> index.types("Plain").clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> type.members().add("other"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> type.supertypes().add("Other"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(index.types("Plain")).containsExactly(type);
    assertThat(index.types("Plain").getFirst().members()).containsExactly("field");
  }

  private static JavaSourceIndex index(final String name, final String source) {
    return JavaSourceIndex.of(Map.of(name, source));
  }

  private static Set<String> members(final JavaSourceIndex index, final String name) {
    return index.types(name).getFirst().members();
  }
}
