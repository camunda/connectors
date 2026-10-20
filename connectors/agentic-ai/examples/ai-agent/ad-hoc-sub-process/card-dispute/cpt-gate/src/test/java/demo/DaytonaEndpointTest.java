package demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.daytona.sdk.Daytona;
import io.daytona.sdk.DaytonaConfig;
import org.junit.jupiter.api.Test;

class DaytonaEndpointTest {
  @Test void syntheticStdoutMatchesOriginalLocalScriptSerializationNotRemoteProof() throws Exception {
    var directory = java.nio.file.Path.of("src/test/resources/round3/skills/chargeback-deadline");
    assertThat(SkillInputs.manifest(directory).get("scripts/deadline.py")).isEqualTo(SkillInputs.SCRIPT_SHA);
    var process = new ProcessBuilder("python3", directory.resolve("scripts/deadline.py").toString(),
        "--charge-date", "2026-09-14", "--charge-date", "2026-09-15",
        "--reason-code", "CN-DUP-01", "--filed-on", "2026-10-20").start();
    assertThat(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    assertThat(process.exitValue()).isZero();
    String stdout = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    assertThat(DaytonaEndpoint.skillStdout()).isEqualTo("chargeback-deadline/scripts/deadline.py: OK\n"
        + stdout + SkillInputs.SCRIPT_SHA + "  chargeback-deadline/scripts/deadline.py\n");
  }

  @Test void sdkUsesOnlyLocalMockForCreateFilesAndExecution() throws Exception {
    try (var endpoint = new DaytonaEndpoint();
        var daytona = new Daytona(new DaytonaConfig.Builder()
            .apiKey(DaytonaEndpoint.FAKE_KEY).apiUrl(endpoint.apiUrl()).build())) {
      var sandbox = daytona.create();
      assertThat(sandbox.getWorkDir()).isEqualTo("/home/daytona");
      var connected = daytona.get(sandbox.getId());
      connected.fs.createFolder("/home/daytona/data", "755");
      connected.fs.uploadFile("synthetic".getBytes(java.nio.charset.StandardCharsets.UTF_8),
          "/home/daytona/data/export.csv");
      assertThat(new String(connected.fs.downloadFile("/home/daytona/data/export.csv"),
          java.nio.charset.StandardCharsets.UTF_8)).contains("Synthetic SDK test export");
      for (String path : SkillInputs.PATHS) {
        byte[] bytes = java.nio.file.Files.readAllBytes(
            java.nio.file.Path.of("src/test/resources/round3/skills/chargeback-deadline").resolve(path));
        String remote = "/home/daytona/chargeback-deadline/" + path;
        connected.fs.uploadFile(bytes, remote);
        assertThat(connected.fs.downloadFile(remote)).isEqualTo(bytes);
      }
      var result = connected.process.executeCommand(DaytonaEndpoint.skillCommand(), null, null, 10);
      assertThat(result.getExitCode()).isZero();
      assertThat(result.getResult()).isEqualTo(DaytonaEndpoint.skillStdout());
      String[] separate = DaytonaEndpoint.skillCommand().split(" && ", 2);
      assertThat(connected.process.executeCommand(separate[0], null, null, 10).getResult())
          .isEqualTo("chargeback-deadline/scripts/deadline.py: OK");
      assertThat(connected.process.executeCommand(separate[1], null, null, 10).getResult())
          .isEqualTo(DaytonaEndpoint.skillStdout().substring(DaytonaEndpoint.skillStdout().indexOf('\n') + 1));
      assertThrows(io.daytona.sdk.exception.DaytonaException.class,
          () -> connected.process.executeCommand("python3 -c 'invented'", null, null, 10));
      endpoint.verifyUsed();
    }
  }

  @Test void sandboxFailureIsExplicitAndRejectsNonDummyCredentials() throws Exception {
    try (var endpoint = new DaytonaEndpoint();
        var daytona = new Daytona(new DaytonaConfig.Builder()
            .apiKey(DaytonaEndpoint.FAKE_KEY).apiUrl(endpoint.apiUrl()).build());
        var wrongKey = new Daytona(new DaytonaConfig.Builder()
            .apiKey("synthetic-wrong-key").apiUrl(endpoint.apiUrl()).build())) {
      endpoint.failSandboxCreation();
      assertThrows(io.daytona.sdk.exception.DaytonaException.class, daytona::create);
      assertThrows(io.daytona.sdk.exception.DaytonaException.class, wrongKey::create);
    }
  }
}
