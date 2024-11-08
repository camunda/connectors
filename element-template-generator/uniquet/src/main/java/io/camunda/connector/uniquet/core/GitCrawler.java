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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.uniquet.dto.OutputElementTemplate;
import io.camunda.connector.uniquet.dto.VersionValue;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public class GitCrawler {

  private static final String RAW_GITHUB_LINK =
      "https://raw.githubusercontent.com/camunda/connectors/%s/%s";
  private final Map<String, Map<Integer, VersionValue>> result = new HashMap<>();
  private final Repository repository;

  public GitCrawler(Repository repository) {
    this.repository = repository;
  }

  public static GitCrawler create(String gitDirectory) {
    if (!gitDirectory.endsWith(File.separator) && !gitDirectory.isEmpty()) {
      gitDirectory = gitDirectory + File.separator;
    }
    try {
      Repository repository = FileRepositoryBuilder.create(new File(gitDirectory + ".git"));
      return new GitCrawler(repository);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Map<String, Map<Integer, VersionValue>> getResult() {
    return result;
  }

  public GitCrawler crawl(String branch) {
    try {
      RevWalk walk = new RevWalk(repository);
      ObjectId mainBranch = repository.resolve("refs/heads/%s".formatted(branch));
      walk.markStart(walk.parseCommit(mainBranch));
      for (RevCommit commit : walk) {
        this.analyzeCommit(commit);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  private void analyzeCommit(RevCommit commit) {
    new ElementTemplateIterator(repository, commit)
        .forEachRemaining(
            elementTemplateFile -> {
              if (result.containsKey(elementTemplateFile.elementTemplate().id())) {
                result
                    .get(elementTemplateFile.elementTemplate().id())
                    .putIfAbsent(
                        elementTemplateFile.elementTemplate().version(),
                        new VersionValue(
                            RAW_GITHUB_LINK.formatted(commit.getName(), elementTemplateFile.path()),
                            elementTemplateFile.connectorRuntime()));
              } else {
                Map<Integer, VersionValue> version = new HashMap<>();
                version.put(
                    elementTemplateFile.elementTemplate().version(),
                    new VersionValue(
                        RAW_GITHUB_LINK.formatted(commit.getName(), elementTemplateFile.path()),
                        elementTemplateFile.connectorRuntime()));
                result.put(elementTemplateFile.elementTemplate().id(), version);
              }
            });
  }

  public GitCrawler persist(String location) {

    try (FileWriter myWriter = new FileWriter(location)) {
      myWriter.write(new ObjectMapper().writeValueAsString(fromMap(this.result)));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  private Map<String, List<OutputElementTemplate>> fromMap(
      Map<String, Map<Integer, VersionValue>> result) {
    return result.entrySet().stream()
        .map(
            stringMapEntry ->
                Map.entry(
                    stringMapEntry.getKey(),
                    stringMapEntry.getValue().entrySet().stream()
                        .map(
                            integerVersionValueEntry ->
                                new OutputElementTemplate(
                                    integerVersionValueEntry.getKey(),
                                    integerVersionValueEntry.getValue().link(),
                                    integerVersionValueEntry.getValue().connectorRuntime()))
                        .sorted((o1, o2) -> o2.version() - o1.version())
                        .toList()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public void close() {
    repository.close();
  }
}
