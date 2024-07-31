package io.camunda.connector.core;

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

  @BeforeAll
  public static void setUp(@TempDir Path tempDir) throws GitAPIException, IOException {
    git = Git.init().setDirectory(tempDir.toFile()).call();
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

    System.out.println(git.getRepository().getDirectory().getPath());
    Map<String, Map<Integer, String>> map =
        GitCrawler.create(git.getRepository().getDirectory().getParentFile().getPath())
            .crawl("master")
            .getResult();

    // Verification that the version 2 is the last commit
    Assertions.assertTrue(map.get("test").get(2).contains(revCommit3.getName()));
    // Verification that the version 1 is the last commit containing version 1
    Assertions.assertTrue(map.get("test").get(1).contains(revCommit2.getName()));
  }
}
