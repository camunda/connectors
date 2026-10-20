package demo;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class CredentialSafety {
  private static final Pattern RAW_KEY = Pattern.compile(
      "(?:sk-[A-Za-z0-9_-]{16,}|AKIA[A-Z0-9]{16}|AIza[A-Za-z0-9_-]{20,}|eyJ[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+|Bearer\\s+[A-Za-z0-9_-]{16,})");

  static void credential(JsonNode credential) throws Exception {
    if (!"SECRET_REFERENCE".equals(credential.path("kind").asText())) {
      throw new AssertionError("cardDisputeLlm must have kind SECRET_REFERENCE");
    }
    JsonNode value = credential.path("value");
    if (value.isTextual()) value = Demo.JSON.readTree(value.asText());
    var keys = new java.util.HashSet<String>();
    value.fieldNames().forEachRemaining(keys::add);
    if (!keys.equals(Set.of("endpoint", "apiKey", "model"))) {
      throw new AssertionError("Saved credential must contain endpoint/apiKey/model keys only");
    }
    for (var item : value) {
      if (!item.isTextual() || !item.asText().matches("camunda\\.secrets\\.[A-Za-z0-9_]+")) {
        throw new AssertionError("Saved credential contains a non-reference field (value withheld)");
      }
    }
  }

  static void noRawKeys(JsonNode node) {
    noRawKeys(node, null);
  }

  static void noRawKeys(JsonNode node, String expectedStubUrl) {
    if (node.isTextual()) {
      if (RAW_KEY.matcher(node.asText()).find()) throw new AssertionError("Raw key pattern found (value withheld)");
      return;
    }
    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        var field = fields.next();
        String key = field.getKey().toLowerCase(java.util.Locale.ROOT);
        if (Set.of("apikey", "api_key", "accesstoken", "access_token", "clientsecret", "client_secret").contains(key)) {
          String value = field.getValue().asText();
          if (!field.getValue().isNull() && !value.isEmpty()
              && !(expectedStubUrl != null && field.getKey().equals("apiKey")
                  && value.equals(DaytonaEndpoint.FAKE_KEY)
                  && node.path("apiUrl").asText().equals(expectedStubUrl))
              && !value.startsWith("camunda.secrets.") && !value.matches("\\{\\{secrets\\.[A-Za-z0-9_]+}}")) {
            throw new AssertionError("Raw credential field found (value withheld)");
          }
        }
        noRawKeys(field.getValue(), expectedStubUrl);
      }
    } else if (node.isArray()) {
      node.forEach(value -> noRawKeys(value, expectedStubUrl));
    }
  }
}
