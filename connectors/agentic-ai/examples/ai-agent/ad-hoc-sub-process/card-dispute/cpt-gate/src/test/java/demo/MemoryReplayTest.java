package demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MemoryReplayTest {
  @Test void ownedCapturedSnapshotAndOriginalsReplayWithoutNetworkOrCertification() throws Exception {
    String file = System.getProperty("gate.memoryReplayFile");
    org.junit.jupiter.api.Assumptions.assumeTrue(file != null, "Explicit owned diagnostic capture required");
    var raw = Demo.JSON.readTree(Files.readString(Path.of(file)));
    assertThat(raw.path("fixtureKind").asText()).isEqualTo("owned-mocked-Daytona-diagnostic-not-certification");
    var originals = new java.util.HashMap<String, com.fasterxml.jackson.databind.JsonNode>();
    raw.path("originals").fields().forEachRemaining(entry -> originals.put(entry.getKey(), entry.getValue()));
    String historyFile = System.getProperty("gate.memoryHistoryFile");
    if (historyFile != null) {
      var history = Demo.JSON.readTree(Files.readString(Path.of(historyFile))).path("history");
      assertThat(history.isArray()).isTrue();
      String agentKey = raw.path("agent").path("context").path("metadata").path("agentInstanceKey").asText();
      assertThat(agentKey).isNotBlank();
      for (var item : history) assertThat(item.path("agentInstanceKey").asText()).isEqualTo(agentKey);
      MemoryEvidence.addNativeResults(originals, history);
    }
    var documents = new java.util.HashSet<String>();
    raw.path("documentIds").forEach(id -> documents.add(id.asText()));
    assertThat(documents).hasSize(5);
    var reader = new MemoryEvidence(uri -> {
      assertThat(raw.path("responses").has(uri.toString())).as("captured exact owned URL").isTrue();
      return raw.path("responses").path(uri.toString());
    });
    reader.verify(raw.path("agent"), null, documents, null, originals);
  }
}
