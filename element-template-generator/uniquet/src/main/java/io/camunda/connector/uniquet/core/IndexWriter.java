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

import static io.camunda.connector.uniquet.core.FileHelper.*;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.uniquet.dto.Connector;
import io.camunda.connector.uniquet.dto.Engine;
import io.camunda.connector.uniquet.dto.OutputElementTemplate;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IndexWriter {

  private final String githubLinkFormat;
  private final File finalFile;
  private final List<File> allElementTemplates;

  private IndexWriter(String gitDirectory, Path finalFile, String connectorDirectory) {
    if (!gitDirectory.endsWith(File.separator) && !gitDirectory.isEmpty()) {
      gitDirectory = gitDirectory + File.separator;
    }
    List<Connector> connectorList =
        ConnectorsFinder.create(Path.of(connectorDirectory)).getAllConnectors();
    this.allElementTemplates =
        Stream.concat(
                connectorList.stream()
                    .map(Connector::versionedElementTemplate)
                    .flatMap(Collection::stream),
                connectorList.stream().map(Connector::currentElementTemplate))
            .toList();
    this.githubLinkFormat =
        "https://raw.githubusercontent.com/camunda/connectors/"
            + getCurrentGitSha256(gitDirectory)
            + "%s";
    this.finalFile = new File(finalFile.toUri());
  }

  public static IndexWriter create(String gitDirectory, String connectorDirectory, Path finalFile) {
    return new IndexWriter(gitDirectory, finalFile, connectorDirectory);
  }

  public void persist() {
    Map<String, Set<OutputElementTemplate>> results = new HashMap<>();
    allElementTemplates.forEach(file -> processVersionedFile(file, results));
    writeToFile(this.finalFile, results);
  }

  private void processVersionedFile(File file, Map<String, Set<OutputElementTemplate>> result) {
    JsonNode jsonNode = toJsonNode(file);
    Integer version = jsonNode.get("version").asInt();
    String key = jsonNode.get("id").asText();
    String link = githubLinkFormat.formatted(file.getPath().split("connectors")[1]);
    String engine =
        Optional.ofNullable(jsonNode.get("engines"))
            .map(jn -> jn.get("camunda"))
            .map(JsonNode::asText)
            .orElse(null);

    result.merge(
        key,
        Set.of(new OutputElementTemplate(version, link, new Engine(engine))),
        (outputElementTemplates, outputElementTemplates2) ->
            Stream.concat(outputElementTemplates.stream(), outputElementTemplates2.stream())
                .collect(
                    Collectors.toCollection(
                        () ->
                            new TreeSet<>(
                                Comparator.comparing(OutputElementTemplate::getVersion)
                                    .reversed()))));
  }
}
