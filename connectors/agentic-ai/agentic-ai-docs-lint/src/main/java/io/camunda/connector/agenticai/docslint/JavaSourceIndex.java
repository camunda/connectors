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
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/**
 * The types of the Java sources of the repository, read with the JDK compiler tree API: the types
 * they declare, import or name as nested types, and the members and supertypes of each declared
 * type.
 */
final class JavaSourceIndex {

  /**
   * A declared type. The binary name has the form {@code package.Outer$Inner}. The supertypes are
   * simple names.
   */
  record Type(String binaryName, Tree.Kind kind, Set<String> members, List<String> supertypes) {

    Type {
      members = Set.copyOf(members);
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
                        errors.add(
                            diagnostic.getSource().getName()
                                + ":"
                                + diagnostic.getLineNumber()
                                + ": "
                                + diagnostic.getMessage(null));
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

  /** The declared types with the simple name, in no particular order. */
  List<Type> types(final String simpleName) {
    return List.copyOf(declared.getOrDefault(simpleName, List.of()));
  }

  /** The simple names of all declared types. */
  Set<String> declaredNames() {
    return new TreeSet<>(declared.keySet());
  }

  /** Whether the sources declare or import the simple name, or it is a {@code java.lang} type. */
  boolean knows(final String simpleName) {
    return declared.containsKey(simpleName)
        || imported.contains(simpleName)
        || nested.contains(simpleName)
        || Class.forName(Object.class.getModule(), "java.lang." + simpleName) != null;
  }

  /**
   * The members of the type and of its declared supertypes. Constructors are listed under the
   * simple name of their type. A supertype is found by its simple name, so all types of that name
   * count. Supertypes that the sources do not declare are skipped.
   */
  Set<String> members(final Type type) {
    final Set<String> members = new LinkedHashSet<>();
    hierarchy(type).forEach(current -> members.addAll(current.members()));
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
        new TreeScanner<Void, String>() {
          @Override
          public Void visitClass(final ClassTree tree, final String outer) {
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
          public Void visitMemberSelect(final MemberSelectTree tree, final String outer) {
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
    for (final Tree member : tree.getMembers()) {
      if (member instanceof final MethodTree method) {
        final String name = method.getName().toString();
        members.add(name.equals("<init>") ? simpleName : name);
      } else if (member instanceof final VariableTree variable) {
        members.add(variable.getName().toString());
      }
    }
    final List<String> supertypes = new ArrayList<>();
    if (tree.getExtendsClause() != null) {
      supertypes.add(simpleName(tree.getExtendsClause()));
    }
    tree.getImplementsClause().forEach(supertype -> supertypes.add(simpleName(supertype)));
    declared
        .computeIfAbsent(simpleName, name -> new ArrayList<>())
        .add(new Type(binaryName, tree.getKind(), members, supertypes));
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
