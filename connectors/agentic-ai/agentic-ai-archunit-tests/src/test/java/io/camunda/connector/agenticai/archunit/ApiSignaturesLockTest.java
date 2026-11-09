/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaGenericArrayType;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.JavaTypeVariable;
import com.tngtech.archunit.core.domain.JavaWildcardType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.lang.reflect.Executable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Locks the public signatures of {@code aiagent-api}, the extension points of the AI Agent. A
 * signature change fails the build until the lock is regenerated, and the failure lists the docs
 * that name each changed type.
 *
 * <p>Regenerate the lock with {@code -Dupdate-doc-lock}.
 */
class ApiSignaturesLockTest {

  private static final Path AGENTIC_AI_DIR =
      Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize().getParent();

  private static final Path REPOSITORY_ROOT = AGENTIC_AI_DIR.getParent().getParent();

  private static final Path LOCK_FILE =
      AGENTIC_AI_DIR.resolve("aiagent-api/docs/api-signatures.lock");

  private static final Set<String> ENUM_METHODS = Set.of("values()", "valueOf(String)");

  /** Superclasses that the kind of the type implies. */
  private static final Set<String> IMPLICIT_SUPERCLASSES = Set.of("Object", "Record", "Enum");

  private static final Set<String> RECORD_METHODS =
      Set.of("equals(Object)", "hashCode()", "toString()");

  @Test
  void publicSignaturesMatchTheLock() throws IOException {
    final List<String> signatures = signatures(publicApiTypes());
    if (updatesTheLock(System.getProperty("update-doc-lock"))) {
      Files.writeString(LOCK_FILE, String.join("\n", signatures) + "\n");
      return;
    }
    final List<String> locked = Files.readAllLines(LOCK_FILE);

    final Set<String> added = new TreeSet<>(signatures);
    added.removeAll(locked);
    final Set<String> removed = new TreeSet<>(locked);
    removed.removeAll(signatures);

    final List<String> differences = new ArrayList<>();
    added.forEach(signature -> differences.add("added: " + signature));
    removed.forEach(signature -> differences.add("removed: " + signature));

    assertThat(differences)
        .as(
            "public signatures of aiagent-api that differ from %s. Update the docs of each changed"
                + " type, then regenerate the lock with -Dupdate-doc-lock. Docs that name the"
                + " changed types:%n%s%n",
            REPOSITORY_ROOT.relativize(LOCK_FILE), docsOfChangedTypes(added, removed))
        .isEmpty();
  }

  @Test
  void signaturesShowTypeParametersWithTheirBoundsAndVarargs() {
    assertThat(signatures(List.of(new ClassFileImporter().importClass(Sample.class))))
        .containsExactly(
            "ApiSignaturesLockTest.Sample#<U extends Number & Runnable> pick(T, String...): U",
            "ApiSignaturesLockTest.Sample#all(String[]): void",
            "io.camunda.connector.agenticai.archunit.ApiSignaturesLockTest.Sample<T extends"
                + " Comparable<T>>: interface");
  }

  @Test
  void onlyAnEmptyOrTrueValueUpdatesTheLock() {
    assertThat(Stream.of("", "true", "false", "no").map(ApiSignaturesLockTest::updatesTheLock))
        .containsExactly(true, true, false, false);
    assertThat(updatesTheLock(null)).isFalse();
  }

  /** Whether the {@code update-doc-lock} property asks to write the lock. */
  private static boolean updatesTheLock(final String property) {
    return (property != null && property.isEmpty()) || "true".equals(property);
  }

  /** The public types of {@code aiagent-api}, nested types included. */
  private static List<JavaClass> publicApiTypes() {
    return new ClassFileImporter()
            .withImportOption(new DoNotIncludeTestsOrTestJars())
            .importPackages(AgenticAiModules.pkg("aiagent.api"))
            .stream()
            .filter(AgenticAiModules.API.root())
            .filter(type -> type.getModifiers().contains(JavaModifier.PUBLIC))
            .toList();
  }

  /**
   * One line per public type and per public member, sorted: {@code pkg.Type[<T extends Bound>]:
   * kind [modifiers] [extends ...] [implements ...] [permits ...]}, {@code
   * Type#<init>(ParamTypes)}, {@code Type#NAME: FieldType} and {@code Type#[modifier ][<T extends
   * Bound> ]method(ParamTypes): ReturnType}. A varargs parameter ends with {@code ...}.
   */
  private static List<String> signatures(final List<JavaClass> types) {
    final Set<String> signatures = new TreeSet<>();
    for (final JavaClass type : types) {
      signatures.add(typeLine(type));
      for (final JavaConstructor constructor : type.getConstructors()) {
        if (isPublic(constructor)) {
          signatures.add(typeName(type) + "#<init>" + parameters(constructor));
        }
      }
      for (final JavaField field : type.getFields()) {
        if (isPublic(field)) {
          signatures.add(typeName(type) + "#" + field.getName() + ": " + name(field.getType()));
        }
      }
      for (final JavaMethod method : type.getMethods()) {
        if (isLocked(type, method)) {
          final String typeParameters = typeParameters(method.getTypeParameters());
          signatures.add(
              typeName(type)
                  + "#"
                  + methodModifier(type, method)
                  + (typeParameters.isEmpty() ? "" : typeParameters + " ")
                  + method.getName()
                  + parameters(method)
                  + ": "
                  + name(method.getReturnType()));
        }
      }
    }
    return List.copyOf(signatures);
  }

  private static String typeLine(final JavaClass type) {
    final Class<?> reflected = type.reflect();
    final List<String> parts = new ArrayList<>();
    parts.add(
        type.getPackageName()
            + "."
            + typeName(type)
            + typeParameters(type.getTypeParameters())
            + ":");
    if (type.isInterface()) {
      parts.add(type.isAnnotation() ? "annotation" : "interface");
    } else {
      parts.add(type.isRecord() ? "record" : type.isEnum() ? "enum" : "class");
    }
    if (reflected.isSealed()) {
      parts.add("sealed");
    }
    if (!type.isInterface() && !type.isRecord() && !type.isEnum()) {
      if (type.getModifiers().contains(JavaModifier.FINAL)) {
        parts.add("final");
      }
      if (type.getModifiers().contains(JavaModifier.ABSTRACT)) {
        parts.add("abstract");
      }
    }
    type.getSuperclass()
        .map(ApiSignaturesLockTest::name)
        .filter(superclass -> !IMPLICIT_SUPERCLASSES.contains(superclass.replaceFirst("<.*", "")))
        .ifPresent(superclass -> parts.add("extends " + superclass));
    final String interfaces =
        type.getInterfaces().stream()
            .map(ApiSignaturesLockTest::name)
            .sorted()
            .collect(Collectors.joining(", "));
    if (!interfaces.isEmpty()) {
      parts.add((type.isInterface() ? "extends " : "implements ") + interfaces);
    }
    if (reflected.isSealed()) {
      parts.add(
          "permits "
              + Arrays.stream(reflected.getPermittedSubclasses())
                  .map(permitted ->
                      permitted.getCanonicalName().substring(type.getPackageName().length() + 1))
                  .sorted()
                  .collect(Collectors.joining(", ")));
    }
    return String.join(" ", parts);
  }

  /** {@code static} and {@code default} methods, and {@code abstract} methods of classes. */
  private static String methodModifier(final JavaClass type, final JavaMethod method) {
    final Set<JavaModifier> modifiers = method.getModifiers();
    if (modifiers.contains(JavaModifier.STATIC)) {
      return "static ";
    }
    if (type.isInterface()) {
      return modifiers.contains(JavaModifier.ABSTRACT) ? "" : "default ";
    }
    return modifiers.contains(JavaModifier.ABSTRACT) ? "abstract " : "";
  }

  private static String parameters(final JavaCodeUnit codeUnit) {
    final String parameters =
        codeUnit.getParameterTypes().stream()
            .map(ApiSignaturesLockTest::name)
            .collect(Collectors.joining(", ", "(", ")"));
    return ((Executable) codeUnit.reflect()).isVarArgs()
        ? parameters.replaceFirst("\\[]\\)$", "...)")
        : parameters;
  }

  /** The type parameters with their bounds, for example {@code <T extends Comparable<T>>}. */
  private static String typeParameters(final List<? extends JavaTypeVariable<?>> variables) {
    return variables.isEmpty()
        ? ""
        : variables.stream()
            .map(variable -> variable.getName() + bounds(variable))
            .collect(Collectors.joining(", ", "<", ">"));
  }

  private static String bounds(final JavaTypeVariable<?> variable) {
    final String bounds =
        variable.getUpperBounds().stream()
            .filter(bound -> !bound.getName().equals(Object.class.getName()))
            .map(ApiSignaturesLockTest::name)
            .collect(Collectors.joining(" & "));
    return bounds.isEmpty() ? "" : " extends " + bounds;
  }

  private static boolean isPublic(final JavaMember member) {
    final Set<JavaModifier> modifiers = member.getModifiers();
    return modifiers.contains(JavaModifier.PUBLIC)
        && !modifiers.contains(JavaModifier.SYNTHETIC)
        && !modifiers.contains(JavaModifier.BRIDGE);
  }

  /**
   * Leaves out {@code values} and {@code valueOf} of enums, and {@code equals}, {@code hashCode}
   * and {@code toString} of records. The class file does not tell whether a record declares them.
   */
  private static boolean isLocked(final JavaClass type, final JavaMethod method) {
    final Set<String> implicit =
        type.isEnum() ? ENUM_METHODS : type.isRecord() ? RECORD_METHODS : Set.of();
    return isPublic(method) && !implicit.contains(method.getName() + parameters(method));
  }

  /** The simple name with generic arguments, for example {@code List<ToolCall>}. */
  private static String name(final JavaType type) {
    return switch (type) {
      case JavaParameterizedType parameterized ->
          name(parameterized.toErasure())
              + parameterized.getActualTypeArguments().stream()
                  .map(ApiSignaturesLockTest::name)
                  .collect(Collectors.joining(", ", "<", ">"));
      case JavaWildcardType wildcard -> wildcardName(wildcard);
      case JavaTypeVariable<?> variable -> variable.getName();
      case JavaGenericArrayType array -> name(array.getComponentType()) + "[]";
      case JavaClass javaClass -> typeName(javaClass);
      default -> type.getName();
    };
  }

  private static String wildcardName(final JavaWildcardType wildcard) {
    if (!wildcard.getLowerBounds().isEmpty()) {
      return "? super " + name(wildcard.getLowerBounds().getFirst());
    }
    final List<JavaType> upper = wildcard.getUpperBounds();
    return upper.isEmpty() || upper.getFirst().getName().equals(Object.class.getName())
        ? "?"
        : "? extends " + name(upper.getFirst());
  }

  /** The simple name, prefixed by the enclosing types, for example {@code ChatResult.Completed}. */
  private static String typeName(final JavaClass type) {
    return type.getEnclosingClass().map(outer -> typeName(outer) + ".").orElse("")
        + type.getSimpleName();
  }

  /** The docs below {@code agentic-ai} that name each changed type in backticks. A hint only. */
  private static String docsOfChangedTypes(final Set<String> added, final Set<String> removed)
      throws IOException {
    final Set<String> changedTypes =
        Stream.concat(added.stream(), removed.stream())
            .map(ApiSignaturesLockTest::changedType)
            .collect(Collectors.toCollection(TreeSet::new));
    final Map<Path, String> docs = new TreeMap<>();
    try (final Stream<Path> files = Files.walk(AGENTIC_AI_DIR)) {
      for (final Path file :
          files
              .filter(path -> path.toString().endsWith(".md"))
              .filter(path -> !path.toString().contains("/target/"))
              .toList()) {
        docs.put(REPOSITORY_ROOT.relativize(file), Files.readString(file));
      }
    }
    final List<String> lines = new ArrayList<>();
    for (final String type : changedTypes) {
      lines.add(type + ":");
      final List<Path> naming =
          docs.entrySet().stream()
              .filter(doc -> doc.getValue().contains("`" + type + "`"))
              .map(Map.Entry::getKey)
              .toList();
      if (naming.isEmpty()) {
        lines.add("  (no doc names this type)");
      }
      naming.forEach(path -> lines.add("  " + path));
    }
    return String.join("\n", lines);
  }

  /** The type of a lock line, without its package, for example {@code ChatResult.Completed}. */
  private static String changedType(final String signature) {
    return signature.replaceFirst("[<#:].*", "").replaceFirst("^(?:[a-z][a-z0-9_]*\\.)+", "");
  }

  interface Sample<T extends Comparable<T>> {

    <U extends Number & Runnable> U pick(T first, String... rest);

    void all(String[] values);
  }
}
