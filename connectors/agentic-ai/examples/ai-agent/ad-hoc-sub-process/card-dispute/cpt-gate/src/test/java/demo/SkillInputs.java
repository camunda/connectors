package demo;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SkillInputs {
  static final List<String> PATHS = List.of("SKILL.md", "references/network-rules.md", "scripts/deadline.py");
  static final String SCRIPT_SHA = "816444bd5c28a64e7ff99c7de4bb6c706e42b3bfe56acc1c98e9089c07fa8416";

  @FunctionalInterface interface Upload {
    JsonNode document(Path path, String contentType) throws Exception;
  }

  static Map<String, String> manifest(Path directory) throws Exception {
    var result = new LinkedHashMap<String, String>();
    for (String relative : PATHS) {
      Path file = directory.resolve(relative);
      if (!Files.isRegularFile(file)) throw new IllegalArgumentException("Missing skill document: " + relative);
      result.put(relative, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
    }
    if (!SCRIPT_SHA.equals(result.get("scripts/deadline.py"))) {
      throw new IllegalArgumentException("deadline.py differs from the approved original script bytes");
    }
    return result;
  }

  static Map<String, Object> upload(Path demo, Map<String, String> approvedManifest,
      String filingDate, Upload upload) throws Exception {
    LocalDate.parse(filingDate);
    Path directory = demo.resolve("skills/chargeback-deadline");
    var actualManifest = manifest(directory);
    if (!actualManifest.equals(approvedManifest)) {
      throw new IllegalArgumentException("Skill bytes differ from the approved manifest");
    }
    var documents = new ArrayList<JsonNode>();
    documents.add(upload.document(demo.resolve("data/statement-2026-09.pdf"), "application/pdf"));
    documents.add(upload.document(demo.resolve("data/transactions-2026-09.csv"), "text/csv"));
    for (String relative : PATHS) {
      documents.add(upload.document(directory.resolve(relative), relative.endsWith(".py") ? "text/x-python" : "text/markdown"));
    }
    var ids = new HashSet<String>();
    var names = List.of("statement-2026-09.pdf", "transactions-2026-09.csv", "SKILL.md", "network-rules.md", "deadline.py");
    for (int index = 0; index < documents.size(); index++) {
      var document = documents.get(index);
      if (document == null || document.path("documentId").asText().isBlank()
          || !ids.add(document.path("documentId").asText())
          || !names.get(index).equals(document.path("metadata").path("fileName").asText())) {
        throw new IllegalArgumentException("Missing, duplicate or misnamed uploaded document: " + names.get(index));
      }
    }
    if (!actualManifest.equals(manifest(directory))) {
      throw new IllegalArgumentException("Skill bytes changed during upload");
    }
    return Map.of("disputeDocuments", List.copyOf(documents), "disputeFilingDate", filingDate,
        "deadlineScriptSha256", actualManifest.get("scripts/deadline.py"),
        "deadlineSkillManifest", Map.copyOf(actualManifest));
  }

  private SkillInputs() {}
}
