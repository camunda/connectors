package demo;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.Map;

final class DaytonaEndpoint implements AutoCloseable {
  static final String FAKE_KEY = "gate-fake-key";
  private static final String ID = "00000000-0000-4000-8000-000000000001";
  private final WireMockServer server = new WireMockServer(
      wireMockConfig().bindAddress("127.0.0.1").dynamicPort());

  DaytonaEndpoint() throws Exception {
    server.start();
    String sandbox = Demo.JSON.writeValueAsString(Map.ofEntries(
        Map.entry("id", ID), Map.entry("name", "gate-mock"), Map.entry("organizationId", "gate"),
        Map.entry("user", "daytona"), Map.entry("env", Map.of()), Map.entry("labels", Map.of()),
        Map.entry("public", false), Map.entry("networkBlockAll", true),
        Map.entry("target", "local"), Map.entry("cpu", 1),
        Map.entry("gpu", 0), Map.entry("memory", 1), Map.entry("disk", 1),
        Map.entry("state", "started"), Map.entry("toolboxProxyUrl", baseUrl() + "/toolbox")));
    server.stubFor(post(urlPathEqualTo("/api/sandbox")).willReturn(okJson(sandbox)));
    server.stubFor(get(urlPathEqualTo("/api/sandbox/" + ID)).willReturn(okJson(sandbox)));
    server.stubFor(get(urlPathEqualTo("/api/sandbox/" + ID + "/toolbox-proxy-url"))
        .willReturn(okJson(Demo.JSON.writeValueAsString(Map.of("url", baseUrl() + "/toolbox")))));
    for (String path : new String[] {"/work-dir", "/user-home-dir"}) {
      server.stubFor(get(urlPathEqualTo("/toolbox/" + ID + path))
          .willReturn(okJson("{\"dir\":\"/home/daytona\"}")));
    }
    server.stubFor(post(urlPathEqualTo("/toolbox/" + ID + "/process/execute"))
        .willReturn(aResponse().withStatus(422).withBody("Unsupported synthetic gate command")));
    server.stubFor(post(urlPathEqualTo("/toolbox/" + ID + "/process/execute"))
        .withRequestBody(matchingJsonPath("$.command", matching(
            "(?s)printf .*sha256sum -c -.*python3 chargeback-deadline/scripts/deadline\\.py.*"
                + "sha256sum chargeback-deadline/scripts/deadline\\.py\\s*")))
        .atPriority(1).willReturn(okJson(Demo.JSON.writeValueAsString(Map.of("exitCode", 0,
            "result", skillStdout())))));
    server.stubFor(post(urlPathEqualTo("/toolbox/" + ID + "/process/execute"))
        .withRequestBody(matchingJsonPath("$.command", matching(
            "(?s)python3 chargeback-deadline/scripts/deadline\\.py.*"
                + "sha256sum chargeback-deadline/scripts/deadline\\.py\\s*")))
        .atPriority(1).willReturn(okJson(Demo.JSON.writeValueAsString(Map.of("exitCode", 0,
            "result", skillStdout().substring(skillStdout().indexOf('\n') + 1))))));
    server.stubFor(post(urlPathEqualTo("/toolbox/" + ID + "/process/execute"))
        .withRequestBody(matchingJsonPath("$.command", matching("(?s)printf .*sha256sum -c -\\s*")))
        .atPriority(1).willReturn(okJson("{\"exitCode\":0,\"result\":\"chargeback-deadline/scripts/deadline.py: OK\"}")));
    server.stubFor(post(urlPathEqualTo("/toolbox/" + ID + "/process/execute"))
        .withRequestBody(matchingJsonPath("$.command", matching("(?s)(mkdir|find) .*")))
        .atPriority(1).willReturn(okJson("{\"exitCode\":0,\"result\":\"\"}")));
    server.stubFor(post(urlPathEqualTo("/toolbox/" + ID + "/files/folder")).willReturn(okJson("{}")));
    server.stubFor(post(urlPathEqualTo("/toolbox/" + ID + "/files/upload")).willReturn(okJson("{}")));
    server.stubFor(get(urlPathEqualTo("/toolbox/" + ID + "/files/download"))
        .willReturn(aResponse().withStatus(404).withBody("Unknown synthetic file")));
    for (String relative : SkillInputs.PATHS) {
      for (String prefix : java.util.List.of("/home/daytona/", "")) {
        server.stubFor(get(urlPathEqualTo("/toolbox/" + ID + "/files/download"))
            .withQueryParam("path", equalTo(prefix + "chargeback-deadline/" + relative))
            .atPriority(1).willReturn(ok().withBody(java.nio.file.Files.readAllBytes(
                skillDirectory().resolve(relative)))));
      }
    }
    server.stubFor(get(urlPathEqualTo("/toolbox/" + ID + "/files/download"))
        .withQueryParam("path", equalTo("/home/daytona/data/export.csv"))
        .atPriority(1).willReturn(ok().withBody("Synthetic SDK test export")));
    server.stubFor(any(anyUrl()).withHeader("Authorization", notMatching(String.format("Bearer %s", FAKE_KEY)))
        .atPriority(0).willReturn(aResponse().withStatus(401).withBody("Mock accepts only its dummy key")));
  }

  private static java.nio.file.Path skillDirectory() {
    return java.nio.file.Path.of("src/test/resources/round3/skills/chargeback-deadline");
  }

  static String skillCommand() {
    return "printf '%s  %s\\n' '" + SkillInputs.SCRIPT_SHA
        + "' 'chargeback-deadline/scripts/deadline.py' | sha256sum -c - && "
        + "python3 chargeback-deadline/scripts/deadline.py --charge-date 2026-09-14 "
        + "--charge-date 2026-09-15 --reason-code CN-DUP-01 --filed-on 2026-10-20 && "
        + "sha256sum chargeback-deadline/scripts/deadline.py";
  }

  static String skillStdout() throws Exception {
    var result = new java.util.LinkedHashMap<String, Object>();
    result.put("skill", "chargeback-deadline");
    result.put("reasonCode", "CN-DUP-01");
    result.put("chargeDates", java.util.List.of("2026-09-14", "2026-09-15"));
    result.put("timeLimitDays", 120);
    result.put("anchorBasis", "last_duplicate_charge");
    result.put("anchorDate", "2026-09-15");
    result.put("lastChargeDate", "2026-09-15");
    result.put("filingDeadline", "2027-01-13");
    result.put("filedOn", "2026-10-20");
    result.put("eligible", true);
    result.put("eligibilityScope", "filing_window_only");
    result.put("daysBetweenCharges", 1);
    result.put("scriptSha256", SkillInputs.SCRIPT_SHA);
    var pythonJson = new com.fasterxml.jackson.core.util.MinimalPrettyPrinter() {
      @Override public void writeObjectFieldValueSeparator(com.fasterxml.jackson.core.JsonGenerator generator)
          throws java.io.IOException { generator.writeRaw(": "); }
      @Override public void writeObjectEntrySeparator(com.fasterxml.jackson.core.JsonGenerator generator)
          throws java.io.IOException { generator.writeRaw(", "); }
      @Override public void writeArrayValueSeparator(com.fasterxml.jackson.core.JsonGenerator generator)
          throws java.io.IOException { generator.writeRaw(", "); }
    };
    return "chargeback-deadline/scripts/deadline.py: OK\n"
        + Demo.JSON.writer(pythonJson).writeValueAsString(new java.util.TreeMap<>(result))
        + "\n" + SkillInputs.SCRIPT_SHA + "  chargeback-deadline/scripts/deadline.py\n";
  }

  private String baseUrl() { return server.baseUrl(); }
  String apiUrl() { return baseUrl() + "/api"; }

  void failSandboxCreation() {
    server.stubFor(post(urlPathEqualTo("/api/sandbox")).atPriority(1)
        .willReturn(aResponse().withStatus(503).withBody("{\"message\":\"Forced gate sandbox failure\"}")));
  }

  void verifyUsed() {
    server.verify(moreThanOrExactly(1), postRequestedFor(urlPathEqualTo("/api/sandbox")));
    server.verify(moreThanOrExactly(1), postRequestedFor(urlPathEqualTo("/toolbox/" + ID + "/process/execute")));
    if (!server.findAllUnmatchedRequests().isEmpty()) {
      throw new AssertionError("Daytona stub received unsupported requests (details withheld)");
    }
  }

  @Override public void close() {
    for (var request : server.findAll(anyRequestedFor(anyUrl()))) {
      System.out.println("Daytona mock request: " + request.getMethod() + " "
          + request.getUrl().split("\\?")[0] + "; dummy authentication="
          + String.format("Bearer %s", FAKE_KEY).equals(request.getHeader("Authorization")));
    }
    server.stop();
  }
}
