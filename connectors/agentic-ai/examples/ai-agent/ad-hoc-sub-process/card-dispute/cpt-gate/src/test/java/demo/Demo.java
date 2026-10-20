package demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Demo {
  static final ObjectMapper JSON = new ObjectMapper();
  static final Path ROOT = Path.of(System.getProperty("gate.demo", "src/test/resources/demo"));

  static String text(String path) throws IOException {
    return Files.readString(ROOT.resolve(path));
  }

  static JsonNode json(String path) throws IOException {
    return JSON.readTree(text(path));
  }

  static Map<String, Object> variables(List<?> documents, String api, String rulesKey) {
    var values = new LinkedHashMap<String, Object>();
    values.put("disputeId", "CPT-" + java.util.UUID.randomUUID());
    values.put("cardholderName", "Jordan Avery");
    values.put("cardLast4", "4417");
    values.put("claim", "I was charged twice for one headphones order from Northwind Electronics in mid-September. I only placed one order.");
    values.put("disputedAmount", 482.17);
    values.put("disputeDocuments", documents);
    values.put("clusterApiUrl", api);
    values.put("cardRulesKey", rulesKey);
    return values;
  }

  static Map<String, Object> draft(String version) {
    return Map.of(
        "decision", "APPROVE",
        "refundAmount", 482.17,
        "reasonCode", "CN-DUP-01",
        "rationale", "Synthetic " + version + " LLM response: mapping test, not policy evaluation.",
        "evidence", List.of("TX-0911", "TX-0912"));
  }

  private Demo() {}
}
