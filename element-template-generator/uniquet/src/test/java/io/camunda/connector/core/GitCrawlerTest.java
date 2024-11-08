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
package io.camunda.connector.core;

import io.camunda.connector.uniquet.core.GitCrawler;
import io.camunda.connector.uniquet.dto.VersionValue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitCrawlerTest {

  private static Git git;
  private static Path newFilePath;
  private static Path pomPath;

  @BeforeAll
  public static void setUp(@TempDir Path tempDir) throws GitAPIException, IOException {
    git = Git.init().setDirectory(tempDir.toFile()).call();
    pomPath = tempDir.resolve("pom.xml");
    newFilePath = tempDir.resolve("element-templates/test.json");
    Files.createDirectories(newFilePath.getParent());
  }

  @AfterAll
  public static void tearDown() {
    git.getRepository().close();
  }

  @Test
  void crawl() throws IOException, GitAPIException {

    // commit 1
    String pomFile = Files.readString(Path.of("src/test/resources/fakepom.xml"));
    Files.write(pomPath, pomFile.getBytes(), StandardOpenOption.CREATE);
    String content1 = Files.readString(Path.of("src/test/resources/commit1.json"));
    Files.write(newFilePath, content1.getBytes(), StandardOpenOption.CREATE);
    git.add().addFilepattern(".").call();
    RevCommit revCommit1 = git.commit().setSign(false).setMessage("Initial commit").call();

    // commit 2
    String content2 =
        Files.readString(Path.of("src/test/resources/commit2_version_unchanged.json"));
    Files.write(newFilePath, content2.getBytes(), StandardOpenOption.CREATE);
    git.add().addFilepattern(".").call();
    RevCommit revCommit2 = git.commit().setSign(false).setMessage("commit 2").call();

    // commit 3
    String content3 = Files.readString(Path.of("src/test/resources/commit3_version_changed.json"));
    Files.write(newFilePath, content3.getBytes(), StandardOpenOption.CREATE);
    git.add().addFilepattern(".").call();
    RevCommit revCommit3 = git.commit().setSign(false).setMessage("commit 3").call();

    Map<String, Map<Integer, VersionValue>> map =
        GitCrawler.create(git.getRepository().getDirectory().getParentFile().getPath())
            .crawl("master")
            .getResult();

    // Verification that the version 2 is the last commit
    Assertions.assertTrue(map.get("test").get(2).link().contains(revCommit3.getName()));
    Assertions.assertTrue(map.get("test").get(2).connectorRuntime().equals("9.9"));
    // Verification that the version 1 is the last commit containing version 1
    Assertions.assertTrue(map.get("test").get(1).link().contains(revCommit2.getName()));
    Assertions.assertFalse(map.get("test").get(1).link().contains(revCommit1.getName()));
  }
}
