package demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

final class PythonEvidence {
  static void validate(String module, String mode, Path input) throws Exception {
    Path log = Path.of("target/" + module + "-" + mode + ".log");
    var process = new ProcessBuilder("python3", "-B", module + ".py", mode, input.toString())
        .redirectErrorStream(true).redirectOutput(log.toFile()).start();
    if (!process.waitFor(30, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new AssertionError("Offline evidence validator timed out: " + module);
    }
    assertThat(process.exitValue()).as("Evidence validator " + module + "; see " + log).isZero();
  }

  static void tone(String mode, com.fasterxml.jackson.databind.JsonNode draft, byte[] policy,
      String approved, int version) throws Exception {
    Path request = Path.of("target/tone-" + mode + "-request.json");
    var bytes = new java.util.ArrayList<Integer>();
    for (byte value : policy) bytes.add(Byte.toUnsignedInt(value));
    Files.writeString(request, Demo.JSON.writeValueAsString(java.util.Map.of(
        "draft", draft, "policyBytes", bytes, "approvedPolicy", approved, "resourceVersion", version)));
    validate("tone_proof", mode, request);
  }

  private PythonEvidence() {}
}
