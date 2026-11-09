/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.docslint;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The Markdown docs and Java sources of the Agentic AI modules and their e2e tests below a
 * repository root. All paths are relative to the root. Symbolic links are not followed.
 */
final class DocTree {

  static final Path AGENTIC_AI = Path.of("connectors/agentic-ai");
  static final Path E2E = Path.of("connectors-e2e-test/connectors-e2e-test-agentic-ai");

  private static final Set<Path> HISTORY_DOCS =
      Set.of(AGENTIC_AI.resolve("docs/breaking-changes.md"));
  private static final Pattern SKIPPED_DIRECTORIES = Pattern.compile("target|node_modules|\\..+");

  private final Path root;
  private final Map<Path, Optional<Doc>> docs = new HashMap<>();

  DocTree(final Path root) {
    this.root = root;
  }

  /** The tree of the repository that holds this module, which is run from its directory. */
  static DocTree ofRepository() {
    final Path moduleDir = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
    return new DocTree(moduleDir.getParent().getParent().getParent());
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

  /**
   * The Markdown docs in scope: every {@code *.md} below the Agentic AI and e2e directories,
   * without ADRs, examples and prompt resources.
   */
  List<Path> docPaths() {
    return files(List.of(AGENTIC_AI, E2E), ".md").stream()
        .filter(path -> !path.startsWith(AGENTIC_AI.resolve("docs/adr")))
        .filter(path -> !path.startsWith(AGENTIC_AI.resolve("examples")))
        .filter(path -> !path.toString().contains("src/main/resources/"))
        .toList();
  }

  /** Whether the doc records history and names types that no longer exist. */
  boolean isHistory(final Path path) {
    return HISTORY_DOCS.contains(path);
  }

  /** The modules of the Agentic AI aggregator pom and the e2e module. */
  List<DocModule> modules() {
    final List<DocModule> modules = new ArrayList<>();
    try {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      final Element project =
          factory
              .newDocumentBuilder()
              .parse(root.resolve(AGENTIC_AI).resolve("pom.xml").toFile())
              .getDocumentElement();
      for (final Element container : childElements(project, "modules")) {
        for (final Element module : childElements(container, "module")) {
          final String name = module.getTextContent().strip();
          modules.add(new DocModule(name, AGENTIC_AI.resolve(name)));
        }
      }
    } catch (final Exception e) {
      throw new IllegalStateException(
          "Cannot read the modules of " + AGENTIC_AI.resolve("pom.xml"), e);
    }
    modules.add(new DocModule(E2E.getFileName().toString(), E2E));
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
