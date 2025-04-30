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

import static io.camunda.connector.uniquet.core.FileHelper.toJsonNode;
import static io.camunda.connector.uniquet.core.FileHelper.writeToFile;

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

  private static final String GITHUB_LINK =
      "https://raw.githubusercontent.com/camunda/connectors/refs/heads/main/%s";
  private final File finalFile;
  private final List<File> allElementTemplates;

  private IndexWriter(List<Connector> connectors, Path finalFile) {
    this.allElementTemplates =
        Stream.concat(
                connectors.stream()
                    .map(Connector::versionedElementTemplate)
                    .flatMap(Collection::stream),
                connectors.stream().map(Connector::currentElementTemplate))
            .toList();
    this.finalFile = new File(finalFile.toUri());
  }

  public static IndexWriter create(List<Connector> connectors, Path finalFile) {
    return new IndexWriter(connectors, finalFile);
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
    String link = GITHUB_LINK.formatted(file.getPath().split("/connectors/")[1]);
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
