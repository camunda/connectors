/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.validator.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class TemplateFinder {

  public static final String VERSIONED_SEGMENT = "versioned";
  private static final String ELEMENT_TEMPLATES_SEGMENT = "element-templates";
  private static final Set<String> SKIPPED_DIRECTORIES =
      Set.of("target", "node_modules", ".git", ".idea", ".m2");

  /**
   * Connector directories whose templates the validator skips entirely. Agentic-AI templates are
   * generated/maintained outside the standard connector workflow and intentionally diverge from the
   * conventions enforced here.
   */
  private static final Set<String> SKIPPED_CONNECTORS = Set.of("agentic-ai");

  private TemplateFinder() {}

  /** Files inside any {@code versioned/} directory are excluded. */
  public static List<Path> find(Path root) {
    return walk(root, false);
  }

  /** All element-template JSONs, including those inside {@code versioned/}. */
  public static List<Path> findAll(Path root) {
    return walk(root, true);
  }

  public static boolean isVersioned(Path path) {
    for (Path segment : path) {
      if (VERSIONED_SEGMENT.equals(segment.toString())) {
        return true;
      }
    }
    return false;
  }

  private static List<Path> walk(Path root, boolean includeVersioned) {
    List<Path> results = new ArrayList<>();
    try {
      Files.walkFileTree(
          root,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
              Path name = dir.getFileName();
              if (name == null) {
                return FileVisitResult.CONTINUE;
              }
              String dirName = name.toString();
              if (SKIPPED_DIRECTORIES.contains(dirName)
                  || SKIPPED_CONNECTORS.contains(dirName)
                  || (!includeVersioned && VERSIONED_SEGMENT.equals(dirName))) {
                return FileVisitResult.SKIP_SUBTREE;
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              if (isElementTemplateJson(file)) {
                results.add(file);
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      throw new RuntimeException("Failed to walk " + root, e);
    }
    results.sort(null);
    return results;
  }

  private static boolean isElementTemplateJson(Path path) {
    if (!path.getFileName().toString().endsWith(".json")) {
      return false;
    }
    for (Path segment : path) {
      if (ELEMENT_TEMPLATES_SEGMENT.equals(segment.toString())) {
        return true;
      }
    }
    return false;
  }
}
