package demo;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

final class Api {
  static final String ANALYST_FEEDBACK = "Retain the existing fraud opinion and clarify the duplicate evidence.";
  private final String base;
  private final boolean basic;
  String expectedDaytonaStubUrl;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  Api(String base, boolean basic) {
    this.base = base;
    this.basic = basic;
  }

  JsonNode get(String path) throws Exception {
    return request("GET", path, null);
  }

  JsonNode post(String path, Object body) throws Exception {
    return request("POST", path, body);
  }

  byte[] binary(String path) throws Exception {
    var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20))
        .header("Accept", "*/*");
    if (basic) request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
        "demo:demo".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    var response = http.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    if (response.statusCode() != 200) throw new IOException("Resource download: HTTP " + response.statusCode());
    return response.body();
  }

  byte[] deployedResource(String key, byte[] expected) throws Exception {
    final byte[][] observed = {null};
    org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20))
        .ignoreExceptionsMatching(error -> error instanceof IOException
            && "Resource download: HTTP 404".equals(error.getMessage()))
        .untilAsserted(() -> {
          observed[0] = binary("/v2/resources/" + key + "/content/binary");
          org.assertj.core.api.Assertions.assertThat(observed[0]).isEqualTo(expected);
        });
    return observed[0];
  }

  private JsonNode request(String method, String path, Object body) throws Exception {
    var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15));
    if (basic) request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString("demo:demo".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    request.header("Content-Type", "application/json");
    request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
        : HttpRequest.BodyPublishers.ofString(Demo.JSON.writeValueAsString(body)));
    var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      // Do not print response bodies: incidents may contain resolved connector inputs.
      throw new IOException(method + " " + path + ": HTTP " + response.statusCode());
    }
    return response.body().isBlank() ? Demo.JSON.createObjectNode() : Demo.JSON.readTree(response.body());
  }

  JsonNode variable(long pi, String name) throws Exception {
    var items = post("/v2/variables/search", Map.of("filter",
        Map.of("processInstanceKey", Long.toString(pi), "name", name), "page", Map.of("limit", 100))).path("items");
    if (items.isEmpty()) throw new AssertionError("Missing variable " + name + " in " + pi);
    if (items.size() != 1) throw new AssertionError("Ambiguous variable " + name + "; specify its scope");
    return variableValue(items.get(0));
  }

  JsonNode scopedVariable(long pi, String scope, String name) throws Exception {
    var response = post("/v2/variables/search", Map.of("filter",
        Map.of("processInstanceKey", Long.toString(pi), "scopeKey", scope, "name", name),
        "page", Map.of("limit", 100)));
    var items = response.path("items");
    if (response.path("page").path("totalItems").asInt(items.size()) != 1 || items.size() != 1) {
      throw new AssertionError("Expected exactly one " + name + " in owned scope " + scope);
    }
    var variable = items.get(0);
    if (!scope.equals(variable.path("scopeKey").asText())
        || !Long.toString(pi).equals(variable.path("processInstanceKey").asText())
        || !name.equals(variable.path("name").asText())) {
      throw new AssertionError("Variable response does not match its owned process, scope and name");
    }
    return variableValue(variable);
  }

  JsonNode taskVariables(String task) throws Exception {
    return post("/v2/user-tasks/" + task + "/variables/search", Map.of("page", Map.of("limit", 100))).path("items");
  }

  JsonNode taskVariable(String task, String name) throws Exception {
    for (var v : taskVariables(task)) {
      if (name.equals(v.path("name").asText())) return variableValue(v);
    }
    throw new AssertionError("Missing task variable " + name);
  }

  JsonNode variableValue(JsonNode variable) throws Exception {
    if (variable.path("isTruncated").asBoolean()) {
      String key = variable.path("variableKey").asText();
      if (key.isBlank()) throw new AssertionError("Truncated variable without variableKey");
      variable = get("/v2/variables/" + key);
      if (variable.path("isTruncated").asBoolean()) throw new AssertionError("Full variable endpoint still truncated");
    }

    return Demo.JSON.readTree(variable.path("value").asText());
  }

  void assertNoRawKeys(long pi) throws Exception {
    var response = post("/v2/variables/search", Map.of("filter",
        Map.of("processInstanceKey", Long.toString(pi)), "page", Map.of("limit", 1000)));
    if (response.path("page").path("totalItems").asInt() > 1000) throw new AssertionError("Variable history exceeds scan bound");
    for (var variable : response.path("items")) {
      var value = variableValue(variable);
      if (expectedDaytonaStubUrl == null && "data".equals(variable.path("name").asText())
          && value.has("apiKey") && !value.path("apiKey").isNull()
          && !"{{secrets.DAYTONA_API_KEY}}".equals(value.path("apiKey").asText())) {
        throw new AssertionError("E2E Daytona apiKey must be absent or an unresolved placeholder (value withheld)");
      }
      CredentialSafety.noRawKeys(value, expectedDaytonaStubUrl);
    }
  }

  JsonNode elements(long pi) throws Exception {
    var response = post("/v2/element-instances/search", Map.of("filter",
        Map.of("processInstanceKey", Long.toString(pi)), "page", Map.of("limit", 1000)));
    if (response.path("page").path("totalItems").asInt() > 1000) throw new AssertionError("Element history exceeds page bound");
    return response.path("items");
  }

  long completed(long pi, String id) throws Exception {
    long count = 0;
    for (var element : elements(pi)) {
      if (id.equals(element.path("elementId").asText()) && "COMPLETED".equals(element.path("state").asText())) count++;
    }
    return count;
  }

  String review(long pi, String exclude) throws Exception {
    return review(pi, exclude, () -> {});
  }

  @FunctionalInterface interface Observation {
    void observe() throws Exception;
  }

  String review(long pi, String exclude, Observation observation) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(180).toNanos();
    while (System.nanoTime() < deadline) {
      observation.observe();
      var tasks = post("/v2/user-tasks/search", Map.of("filter", Map.of(
          "processInstanceKey", Long.toString(pi), "elementId", "ReviewDraftedDecision", "state", "CREATED"),
          "page", Map.of("limit", 100))).path("items");
      for (var task : tasks) {
        String key = task.path("userTaskKey").asText();
        if (!key.equals(exclude)) return key;
      }
      var incidents = post("/v2/incidents/search", Map.of("filter",
          Map.of("processInstanceKey", Long.toString(pi), "state", "ACTIVE"), "page", Map.of("limit", 1)));
      if (!incidents.path("items").isEmpty()) throw new AssertionError("Active incident in gate instance " + pi);
      Thread.sleep(1000);
    }
    throw new AssertionError("No new ReviewDraftedDecision within 180s: " + pi);
  }

  void complete(String task, String verdict) throws Exception {
    post("/v2/user-tasks/" + task + "/completion", Map.of("variables", Map.of(
        "analystVerdict", verdict, "analystFeedback", ANALYST_FEEDBACK)));
  }
}
