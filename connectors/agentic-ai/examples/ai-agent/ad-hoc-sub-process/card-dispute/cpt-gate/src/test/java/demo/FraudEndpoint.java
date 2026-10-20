package demo;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.concurrent.atomic.AtomicBoolean;

final class FraudEndpoint implements AutoCloseable {
  private final String taskId = "gate-fraud-" + java.util.UUID.randomUUID();
  private final AtomicBoolean serverError = new AtomicBoolean();
  private final WireMockServer server = new WireMockServer(
      wireMockConfig().bindAddress("127.0.0.1").dynamicPort().disableRequestJournal()
          .notifier(new com.github.tomakehurst.wiremock.common.Notifier() {
            public void info(String message) { }
            public void error(String message) { serverError.set(true); }
            public void error(String message, Throwable cause) { serverError.set(true); }
          }));
  private final AtomicBoolean breached = new AtomicBoolean();
  private final AtomicBoolean missingAuth = new AtomicBoolean();
  private final java.util.concurrent.atomic.AtomicInteger sends = new java.util.concurrent.atomic.AtomicInteger();
  private final java.util.concurrent.atomic.AtomicInteger polls = new java.util.concurrent.atomic.AtomicInteger();
  private final boolean real;
  private final java.util.concurrent.ScheduledExecutorService callbacks =
      java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
  private final AtomicBoolean pushDelivered = new AtomicBoolean();
  private final AtomicBoolean pushFailed = new AtomicBoolean();

  FraudEndpoint(boolean real) {
    this.real = real;
    server.start();
    server.addMockServiceRequestListener((request, response) -> {
      if (request.getBodyAsString().contains("\"SendMessage\"")) sends.incrementAndGet();
      if (request.getBodyAsString().contains("\"GetTask\"")) polls.incrementAndGet();
      if (!real && request.getBodyAsString().contains("\"SendMessage\"") && response.getStatus() == 200) {
        try {
          var config = Demo.JSON.readTree(request.getBodyAsString()).path("params")
              .path("configuration").path("taskPushNotificationConfig");
          String url = config.path("url").asText();
          String token = config.path("token").asText();
          if (url.isBlank() || token.isBlank()) throw new IllegalArgumentException("Missing push config");
          callbacks.schedule(() -> push(url, token), 1, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception error) {
          pushFailed.set(true);
        }
      }
      String authorization = request.getHeader("Authorization");
      if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) missingAuth.set(true);
    });
    if (real) {
      // Block repeated SendMessage before proxying: a failed assertion alone cannot limit real calls.
      server.addMockServiceRequestListener((request, response) -> {
        if (request.getBodyAsString().contains("\"SendMessage\"") && response.getStatus() == 409) breached.set(true);
      });
      server.stubFor(post(urlEqualTo("/a2a")).withRequestBody(matchingJsonPath("$.method", equalTo("SendMessage")))
          .inScenario("one-conversation").whenScenarioStateIs("Started")
          .willSetStateTo("sent").willReturn(aResponse().proxiedFrom("http://localhost:38090")));
      server.stubFor(post(urlEqualTo("/a2a")).withRequestBody(matchingJsonPath("$.method", equalTo("SendMessage")))
          .inScenario("one-conversation").whenScenarioStateIs("sent")
          .willReturn(aResponse().withStatus(409).withBody("{\"error\":\"One real A2A conversation limit\"}")));
    } else {
      server.stubFor(post(urlEqualTo("/a2a")).withRequestBody(matchingJsonPath("$.method", equalTo("SendMessage")))
          .willReturn(okJson(task("TASK_STATE_SUBMITTED"))));
    }
  }

  private void push(String url, String token) {
    try {
      var http = java.net.http.HttpClient.newHttpClient();
      String body = Demo.JSON.readTree(task("TASK_STATE_COMPLETED")).path("result").toString();
      var base = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
          .timeout(java.time.Duration.ofSeconds(20)).header("Content-Type", "application/json");
      int denied = http.send(base.POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),
          java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
      if (denied != 401) throw new AssertionError("Push without token must be rejected");
      int status = http.send(base.header("x-a2a-notification-token", token)
          .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),
          java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
      if (status < 200 || status >= 300) throw new AssertionError("Authenticated push rejected");
      pushDelivered.set(true);
    } catch (Exception | AssertionError error) {
      pushFailed.set(true);
    }
  }

  private String task(String state) {
    return "{\"jsonrpc\":\"2.0\",\"id\":\"gate\",\"result\":{\"task\":{\"id\":\"" + taskId + "\",\"status\":{\"state\":\""
        + state + "\",\"message\":{\"role\":\"ROLE_AGENT\",\"parts\":[{\"text\":\"RISK: LOW\\nCONFIDENCE: 0.95\\nSIGNALS: TX-0911 and TX-0912 are duplicate processing; no fraud indicators.\"}]}}}}}";
  }

  String baseUrl() { return server.baseUrl(); }
  boolean real() { return real; }

  void verifyOneSend() {
    if (sends.get() != 1) throw new AssertionError("Expected exactly one A2A send; count=" + sends.get());
    if (breached.get()) throw new AssertionError("Repeated real A2A SendMessage blocked by guard");
  }

  void verifyPush() {
    if (polls.get() != 0 || !pushDelivered.get() || pushFailed.get()) {
      throw new AssertionError("Expected authenticated push delivery, rejected tokenless request and zero polling (values withheld)");
    }
  }

  void verifyAuthenticated() {
    if (missingAuth.get() || serverError.get()) {
      throw new AssertionError("A2A requests must carry bearer authentication (values withheld)");
    }
  }

  @Override public void close() {
    callbacks.shutdownNow();
    server.stop();
  }
}
