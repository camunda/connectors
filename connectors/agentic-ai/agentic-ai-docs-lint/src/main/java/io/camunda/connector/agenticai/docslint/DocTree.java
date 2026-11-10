/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.docslint;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The Markdown docs and Java sources below the root of a {@link DocsLintConfig}. All paths are
 * relative to the root. Symbolic links are not followed.
 */
final class DocTree {

  private static final Pattern SKIPPED_DIRECTORIES = Pattern.compile("target|node_modules|\\..+");

  private final DocsLintConfig config;
  private final Path root;
  private final List<PathMatcher> excludedDocs;
  private final Map<Path, Optional<Doc>> docs = new HashMap<>();

  DocTree(final DocsLintConfig config) {
    this.config = config;
    this.root = config.root();
    this.excludedDocs =
        config.excludedDocs().stream()
            .map(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob))
            .toList();
  }

  boolean exists(final Path path) {
    return Files.exists(root.resolve(path));
  }

  /** The parsed doc, or empty when the path is no regular file. Each doc is parsed once. */
  Optional<Doc> doc(final Path path) {
    return docs.computeIfAbsent(
        path,
        key -> {
          final Path file = root.resolve(key);
          return Files.isRegularFile(file)
              ? Optional.of(MarkdownParser.parse(key, read(file)))
              : Optional.empty();
        });
  }

  /** The Markdown docs in scope: every {@code *.md} below the doc roots that is not excluded. */
  List<Path> docPaths() {
    return files(config.docRoots(), ".md").stream()
        .filter(path -> excludedDocs.stream().noneMatch(matcher -> matcher.matches(path)))
        .toList();
  }

  /**
   * The parsed doc whose names are checked against the code, or empty for a history doc. A history
   * doc may name types that no longer exist.
   */
  Optional<Doc> currentDoc(final Path path) {
    return isHistory(path) ? Optional.empty() : doc(path);
  }

  /** The parsed docs in scope without the history docs. */
  Stream<Doc> currentDocs() {
    return docPaths().stream().flatMap(path -> currentDoc(path).stream());
  }

  /** The modules of the modules pom, then the extra modules. */
  List<DocModule> modules() {
    final List<DocModule> modules = new ArrayList<>();
    final Path pom = config.modulesPom();
    final Path pomDir = root.relativize(root.resolve(pom).getParent());
    try {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      final Element project =
          factory.newDocumentBuilder().parse(root.resolve(pom).toFile()).getDocumentElement();
      for (final Element container : childElements(project, "modules")) {
        for (final Element module : childElements(container, "module")) {
          final String name = module.getTextContent().strip();
          modules.add(new DocModule(name, pomDir.resolve(name)));
        }
      }
    } catch (final Exception e) {
      throw new IllegalStateException("Cannot read the modules of " + pom, e);
    }
    config
        .extraModules()
        .forEach(dir -> modules.add(new DocModule(dir.getFileName().toString(), dir)));
    return modules;
  }

  /** The regular files with the extension below the directory, sorted. */
  List<Path> files(final Path directory, final String extension) {
    return files(List.of(directory), extension);
  }

  /** The Java sources of the whole repository, by path and content. */
  Map<String, String> javaSources() {
    final Map<String, String> sources = new LinkedHashMap<>();
    for (final Path path : files(List.of(Path.of("")), ".java")) {
      sources.put(path.toString(), read(root.resolve(path)));
    }
    return sources;
  }

  private List<Path> files(final List<Path> directories, final String extension) {
    final List<Path> files = new ArrayList<>();
    for (final Path directory : directories) {
      final Path start = root.resolve(directory);
      if (!Files.isDirectory(start)) {
        continue;
      }
      try {
        Files.walkFileTree(
            start,
            new SimpleFileVisitor<>() {
              @Override
              public FileVisitResult preVisitDirectory(
                  final Path visited, final BasicFileAttributes attributes) {
                return !visited.equals(start)
                        && SKIPPED_DIRECTORIES.matcher(visited.getFileName().toString()).matches()
                    ? FileVisitResult.SKIP_SUBTREE
                    : FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult visitFile(
                  final Path file, final BasicFileAttributes attributes) {
                if (attributes.isRegularFile() && file.toString().endsWith(extension)) {
                  files.add(root.relativize(file));
                }
                return FileVisitResult.CONTINUE;
              }
            });
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return files.stream().sorted().toList();
  }

  private boolean isHistory(final Path path) {
    return config.historyDocs().contains(path);
  }

  private static List<Element> childElements(final Element parent, final String name) {
    final List<Element> elements = new ArrayList<>();
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof final Element element && name.equals(element.getLocalName())) {
        elements.add(element);
      }
    }
    return elements;
  }

  private static String read(final Path file) {
    try {
      return Files.readString(file);
    } catch (final IOException e) {
      throw new UncheckedIOException("Cannot read " + file, e);
    }
  }
}
