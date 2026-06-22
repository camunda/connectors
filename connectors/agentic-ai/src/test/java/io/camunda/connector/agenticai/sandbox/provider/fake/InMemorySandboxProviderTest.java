/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.provider.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.sandbox.spi.ExecRequest;
import io.camunda.connector.agenticai.sandbox.spi.ExecResult;
import io.camunda.connector.agenticai.sandbox.spi.FileInfo;
import io.camunda.connector.agenticai.sandbox.spi.Match;
import io.camunda.connector.agenticai.sandbox.spi.SandboxException;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemorySandboxProviderTest {

  private InMemorySandboxProvider provider;
  private SandboxSession session;

  @BeforeEach
  void setUp() {
    provider = new InMemorySandboxProvider();
    session = provider.create(SandboxSpec.defaults());
  }

  @Test
  void writeAndRead_shouldReturnSameContent() {
    byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
    session.fs().write("/workspace/hello.txt", content);
    assertThat(session.fs().read("/workspace/hello.txt")).isEqualTo(content);
  }

  @Test
  void stat_textFile_shouldNotBeBinary() {
    session.fs().write("/workspace/readme.txt", "some text".getBytes(StandardCharsets.UTF_8));
    FileInfo info = session.fs().stat("/workspace/readme.txt");
    assertThat(info.isBinary()).isFalse();
    assertThat(info.contentType()).isEqualTo("text/plain");
    assertThat(info.size()).isEqualTo(9L);
  }

  @Test
  void stat_binaryFile_shouldBeBinary() {
    byte[] binary = new byte[] {0x25, 0x50, 0x44, 0x46, 0x00, 0x01}; // PDF magic + NUL
    session.fs().write("/workspace/doc.pdf", binary);
    FileInfo info = session.fs().stat("/workspace/doc.pdf");
    assertThat(info.isBinary()).isTrue();
    assertThat(info.contentType()).isEqualTo("application/pdf");
  }

  @Test
  void list_shouldReturnOnlyEntriesUnderPrefix() {
    session.fs().write("/dir/a.txt", "a".getBytes(StandardCharsets.UTF_8));
    session.fs().write("/dir/b.txt", "b".getBytes(StandardCharsets.UTF_8));
    session.fs().write("/other.txt", "other".getBytes(StandardCharsets.UTF_8));

    List<FileInfo> listed = session.fs().list("/dir");
    assertThat(listed).hasSize(2);
    assertThat(listed)
        .extracting(FileInfo::path)
        .containsExactlyInAnyOrder("/dir/a.txt", "/dir/b.txt");
  }

  @Test
  void exec_defaultEcho_shouldReturnCommandAsStdout() {
    ExecResult result = session.exec(ExecRequest.of("ls -la"));
    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.stdout()).isEqualTo("ls -la");
    assertThat(result.stderr()).isEmpty();
  }

  @Test
  void exec_customHandler_shouldReturnCustomResult() {
    provider =
        new InMemorySandboxProvider(req -> new ExecResult(1, "", "command not found", false));
    session = provider.create(SandboxSpec.defaults());

    ExecResult result = session.exec(ExecRequest.of("nonexistent"));
    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stdout()).isEmpty();
    assertThat(result.stderr()).isEqualTo("command not found");
  }

  @Test
  void search_shouldFindMatchingLines() {
    String content = "line one\nhello world\nline three\n";
    session.fs().write("/workspace/notes.txt", content.getBytes(StandardCharsets.UTF_8));

    List<Match> matches = session.fs().search("/workspace", "hello");
    assertThat(matches).hasSize(1);
    assertThat(matches.get(0).path()).isEqualTo("/workspace/notes.txt");
    assertThat(matches.get(0).line()).isEqualTo(2);
    assertThat(matches.get(0).text()).isEqualTo("hello world");
  }

  @Test
  void read_missingFile_shouldThrowSandboxException() {
    assertThatThrownBy(() -> session.fs().read("/nonexistent.txt"))
        .isInstanceOf(SandboxException.class)
        .hasMessageContaining("File not found");
  }

  @Test
  void connectHandle_shouldReturnSameFsState() {
    session.fs().write("/workspace/data.txt", "persistent".getBytes(StandardCharsets.UTF_8));
    var handle = session.handle();

    SandboxSession reconnected = provider.connect(handle);
    assertThat(reconnected.fs().read("/workspace/data.txt"))
        .isEqualTo("persistent".getBytes(StandardCharsets.UTF_8));
  }
}
