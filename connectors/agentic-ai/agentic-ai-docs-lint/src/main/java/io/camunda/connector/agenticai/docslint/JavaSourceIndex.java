/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.docslint;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.jspecify.annotations.Nullable;

/**
 * The types of the Java sources of the repository, read with the JDK compiler tree API: the types
 * they declare, import or name as nested types, and the members and supertypes of each declared
 * type.
 */
final class JavaSourceIndex {

  /**
   * A declared type. The binary name has the form {@code package.Outer$Inner}. The inherited
   * members are the members without the private ones. The supertypes are simple names.
   */
  record Type(
      String binaryName,
      Tree.Kind kind,
      Set<String> members,
      Set<String> inheritedMembers,
      List<String> supertypes) {

    Type {
      members = Set.copyOf(members);
      inheritedMembers = Set.copyOf(inheritedMembers);
      supertypes = List.copyOf(supertypes);
    }

    boolean isInterface() {
      return kind == Tree.Kind.INTERFACE || kind == Tree.Kind.ANNOTATION_TYPE;
    }

    boolean isEnum() {
      return kind == Tree.Kind.ENUM;
    }

    String simpleName() {
      return binaryName.substring(
          Math.max(binaryName.lastIndexOf('.'), binaryName.lastIndexOf('$')) + 1);
    }

    String qualifiedName() {
      return binaryName.replace('$', '.');
    }
  }

  private static final Pattern DOT = Pattern.compile("\\.");

  private final Map<String, List<Type>> declared = new HashMap<>();
  private final Set<String> imported = new HashSet<>();
  private final Set<String> importedNames = new HashSet<>();
  private final Set<String> nested = new HashSet<>();

  /**
   * Parses the sources, given by file name and content. A syntax error fails with the file and the
   * line.
   */
  static JavaSourceIndex of(final Map<String, String> sources) {
    final List<String> errors = new ArrayList<>();
    final List<JavaFileObject> files =
        sources.entrySet().stream()
            .<JavaFileObject>map(source -> new Source(source.getKey(), source.getValue()))
            .toList();
    final JavacTask task =
        (JavacTask)
            ToolProvider.getSystemJavaCompiler()
                .getTask(
                    null,
                    null,
                    diagnostic -> {
                      if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                        errors.add(error(diagnostic));
                      }
                    },
                    List.of("-proc:none"),
                    null,
                    files);
    final JavaSourceIndex index = new JavaSourceIndex();
    try {
      for (final CompilationUnitTree unit : task.parse()) {
        index.add(unit);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    if (!errors.isEmpty()) {
      throw new IllegalStateException("Java parse errors:\n" + String.join("\n", errors));
    }
    return index;
  }

  /**
   * The error line of a diagnostic: the file and line, then the message. A diagnostic without a
   * source gives only the message.
   */
  static String error(final Diagnostic<? extends JavaFileObject> diagnostic) {
    final @Nullable JavaFileObject source = diagnostic.getSource();
    return source == null
        ? diagnostic.getMessage(null)
        : source.getName() + ":" + diagnostic.getLineNumber() + ": " + diagnostic.getMessage(null);
  }

  /** The declared types with the simple name, in no particular order. */
  List<Type> types(final String simpleName) {
    return List.copyOf(declared.getOrDefault(simpleName, List.of()));
  }

  /**
   * Up to five simple names of declared types that are similar to the name: one contains the other,
   * or they differ in at most two characters. The closest come first, ties in alphabetical order.
   * Case is ignored.
   */
  List<String> similarNames(final String name) {
    final String lower = name.toLowerCase(Locale.ROOT);
    return declared.keySet().stream()
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

  /** Whether the sources declare or import the simple name, or it is a {@code java.lang} type. */
  boolean knows(final String simpleName) {
    return declared.containsKey(simpleName)
        || imported.contains(simpleName)
        || nested.contains(simpleName)
        || Class.forName(Object.class.getModule(), "java.lang." + simpleName) != null;
  }

  /** Whether the sources import the qualified type, one of its outer types or its package. */
  boolean imports(final String qualifiedName) {
    return importedNames.stream()
        .anyMatch(name ->
            qualifiedName.equals(name)
                || qualifiedName.startsWith(
                    name.endsWith(".*") ? name.replaceFirst("\\*$", "") : name + "."));
  }

  /**
   * The members of the type and the inherited members of its declared supertypes, without
   * constructors. A supertype is found by its simple name, so all types of that name count.
   * Supertypes that the sources do not declare are skipped.
   */
  Set<String> members(final Type type) {
    final Set<String> members = new LinkedHashSet<>(type.members());
    hierarchy(type).stream()
        .filter(current -> !current.equals(type))
        .forEach(current -> members.addAll(current.inheritedMembers()));
    return members;
  }

  /**
   * The supertypes in the hierarchy of the type that the sources do not declare, by simple name,
   * each with the qualified name of the type that extends it. {@code Object} counts for every type,
   * {@code Record} for a record and {@code Enum} for an enum.
   */
  Map<String, String> undeclaredSupertypes(final Type type) {
    final Map<String, String> undeclared = new TreeMap<>();
    for (final Type current : hierarchy(type)) {
      Stream.concat(current.supertypes().stream(), implicitSupertypes(current))
          .filter(name -> types(name).isEmpty())
          .forEach(name -> undeclared.putIfAbsent(name, current.qualifiedName()));
    }
    return undeclared;
  }

  /**
   * The names of the public and declared methods and fields of the JDK type with the simple name,
   * including inherited ones. The type is found in {@code java.lang} or by the imports of the
   * sources and loaded by the platform class loader. Empty if the type is no JDK type.
   */
  Optional<Set<String>> jdkMembers(final String simpleName) {
    return Stream.concat(
            Stream.of("java.lang." + simpleName),
            importedNames.stream().flatMap(name -> importCandidate(name, simpleName).stream()))
        .filter(name -> name.startsWith("java.") || name.startsWith("javax."))
        .flatMap(name -> load(name).stream())
        .findFirst()
        .map(JavaSourceIndex::memberNames);
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

  private Set<Type> hierarchy(final Type type) {
    final Set<Type> visited = new LinkedHashSet<>();
    final Deque<Type> pending = new ArrayDeque<>(List.of(type));
    while (!pending.isEmpty()) {
      final Type current = pending.pop();
      if (visited.add(current)) {
        current.supertypes().forEach(name -> pending.addAll(types(name)));
      }
    }
    return visited;
  }

  private static Stream<String> implicitSupertypes(final Type type) {
    return switch (type.kind()) {
      case RECORD -> Stream.of("Object", "Record");
      case ENUM -> Stream.of("Object", "Enum");
      default -> Stream.of("Object");
    };
  }

  private static Optional<String> importCandidate(final String imported, final String simpleName) {
    if (imported.endsWith("." + simpleName)) {
      return Optional.of(imported);
    }
    return imported.endsWith(".*")
        ? Optional.of(imported.substring(0, imported.length() - 1) + simpleName)
        : Optional.empty();
  }

  private static Optional<Class<?>> load(final String name) {
    try {
      return Optional.of(Class.forName(name, false, ClassLoader.getPlatformClassLoader()));
    } catch (final ClassNotFoundException | LinkageError e) {
      return Optional.empty();
    }
  }

  private static Set<String> memberNames(final Class<?> type) {
    final Set<String> names = new TreeSet<>();
    Stream.concat(Arrays.stream(type.getMethods()), Arrays.stream(type.getDeclaredMethods()))
        .map(Method::getName)
        .forEach(names::add);
    Stream.concat(Arrays.stream(type.getFields()), Arrays.stream(type.getDeclaredFields()))
        .map(Field::getName)
        .forEach(names::add);
    return names;
  }

  private void add(final CompilationUnitTree unit) {
    final String packagePrefix = unit.getPackageName() == null ? "" : unit.getPackageName() + ".";
    for (final ImportTree anImport : unit.getImports()) {
      importedNames.add(anImport.getQualifiedIdentifier().toString());
      DOT.splitAsStream(anImport.getQualifiedIdentifier().toString())
          .filter(segment -> Character.isUpperCase(segment.charAt(0)))
          .forEach(imported::add);
    }
    unit.accept(
        new TreeScanner<@Nullable Void, @Nullable String>() {
          @Override
          public @Nullable Void visitClass(final ClassTree tree, final @Nullable String outer) {
            final String simpleName = tree.getSimpleName().toString();
            if (simpleName.isEmpty()) {
              return super.visitClass(tree, outer);
            }
            final String binaryName =
                outer == null ? packagePrefix + simpleName : outer + "$" + simpleName;
            declare(binaryName, simpleName, tree);
            return super.visitClass(tree, binaryName);
          }

          @Override
          public @Nullable Void visitMemberSelect(
              final MemberSelectTree tree, final @Nullable String outer) {
            final String name = tree.getIdentifier().toString();
            final String qualifier = tree.getExpression().toString();
            final String last = qualifier.substring(qualifier.lastIndexOf('.') + 1);
            if (startsUpperCase(name) && startsUpperCase(last)) {
              nested.add(name);
            }
            return super.visitMemberSelect(tree, outer);
          }
        },
        null);
  }

  private void declare(final String binaryName, final String simpleName, final ClassTree tree) {
    final Set<String> members = new HashSet<>();
    final Set<String> inheritedMembers = new HashSet<>();
    for (final Tree member : tree.getMembers()) {
      if (member instanceof final MethodTree method && !method.getName().contentEquals("<init>")) {
        members.add(method.getName().toString());
        if (!method.getModifiers().getFlags().contains(Modifier.PRIVATE)) {
          inheritedMembers.add(method.getName().toString());
        }
      } else if (member instanceof final VariableTree variable) {
        members.add(variable.getName().toString());
        if (!variable.getModifiers().getFlags().contains(Modifier.PRIVATE)) {
          inheritedMembers.add(variable.getName().toString());
        }
      }
    }
    final List<String> supertypes = new ArrayList<>();
    if (tree.getExtendsClause() != null) {
      supertypes.add(simpleName(tree.getExtendsClause()));
    }
    tree.getImplementsClause().forEach(supertype -> supertypes.add(simpleName(supertype)));
    declared
        .computeIfAbsent(simpleName, name -> new ArrayList<>())
        .add(new Type(binaryName, tree.getKind(), members, inheritedMembers, supertypes));
  }

  private static String simpleName(final Tree typeTree) {
    final String text = typeTree.toString().replaceFirst("<.*$", "");
    return text.substring(text.lastIndexOf('.') + 1);
  }

  private static boolean startsUpperCase(final String text) {
    return !text.isEmpty() && Character.isUpperCase(text.charAt(0));
  }

  private static final class Source extends SimpleJavaFileObject {

    private final String content;

    Source(final String name, final String content) {
      super(URI.create("string:///" + name), Kind.SOURCE);
      this.content = content;
    }

    @Override
    public String getName() {
      return toUri().getPath().substring(1);
    }

    @Override
    public CharSequence getCharContent(final boolean ignoreEncodingErrors) {
      return content;
    }
  }
}
