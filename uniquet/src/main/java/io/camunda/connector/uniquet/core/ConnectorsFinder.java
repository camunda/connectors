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
package io.camunda.connector.uniquet.core;

import static io.camunda.connector.uniquet.core.FileHelper.getBaseName;

import io.camunda.connector.uniquet.dto.Connector;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class ConnectorsFinder {

  private static final String ELEMENT_TEMPLATES = "element-templates";
  private static final String VERSIONED = "versioned";
  private final List<Connector> connectors;

  public ConnectorsFinder(Path connectorPath) {
    try (Stream<Path> files = Files.walk(connectorPath)) {
      this.connectors =
          files
              .map(path -> new File(path.toUri()))
              .filter(File::isDirectory)
              .filter(this::containsElementTemplates)
              .map(this::getElementTemplateDirectory)
              .flatMap(this::findVersionedConnectors)
              .toList();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static ConnectorsFinder create(Path connectorPath) {
    return new ConnectorsFinder(connectorPath);
  }

  private File getElementTemplateDirectory(File file) {
    return Arrays.stream(Objects.requireNonNull(file.listFiles()))
        .filter(file1 -> file1.getName().endsWith(ELEMENT_TEMPLATES))
        .findFirst()
        .orElseThrow();
  }

  private Stream<Connector> findVersionedConnectors(File file) {
    File[] files = Objects.requireNonNull(file.listFiles());
    return Arrays.stream(files)
        .filter(f -> f.getName().equals(VERSIONED))
        .findFirst()
        .map(
            versionedDirectory ->
                Arrays.stream(files)
                    .filter(File::isFile)
                    .map(currentFile -> this.mapToConnector(currentFile, versionedDirectory)))
        .orElse(
            Arrays.stream(files)
                .filter(File::isFile)
                .map(currentFile -> new Connector(currentFile, List.of())));
  }

  private Connector mapToConnector(File current, File versionedDirectory) {
    return new Connector(
        current,
        Arrays.stream(Objects.requireNonNull(versionedDirectory.listFiles()))
            .filter(file -> getBaseName(file).contains(getBaseName(current)))
            .toList());
  }

  private boolean containsElementTemplates(File directory) {
    if (directory == null || !directory.isDirectory()) {
      return false;
    }
    return Arrays.stream(Objects.requireNonNull(directory.listFiles()))
        .anyMatch(file -> file.getName().endsWith(ELEMENT_TEMPLATES));
  }

  public List<Connector> getAllConnectors() {
    return connectors;
  }
}
