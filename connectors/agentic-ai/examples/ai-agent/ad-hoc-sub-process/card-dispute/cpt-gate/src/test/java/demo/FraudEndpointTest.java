package demo;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FraudEndpointTest {
  @Test
  void stubPushesCompletedWithoutExternalCalls() throws Exception {
    var receiver = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    var delivered = new java.util.concurrent.atomic.AtomicBoolean();
    receiver.createContext("/push", exchange -> {
      boolean authenticated = "synthetic-unit-token".equals(exchange.getRequestHeaders().getFirst("x-a2a-notification-token"));
      if (authenticated) {
        var body = Demo.JSON.readTree(exchange.getRequestBody());
        var status = body.path("task").path("status");
        delivered.set(status.path("state").asText().equals("TASK_STATE_COMPLETED")
            && status.path("message").path("parts").get(0).path("text").asText()
                .startsWith("RISK: LOW\nCONFIDENCE: 0.95\n"));
      }
      exchange.sendResponseHeaders(authenticated ? 202 : 401, -1);
      exchange.close();
    });
    receiver.start();
    try (var fraud = new FraudEndpoint(false)) {
      var client = HttpClient.newHttpClient();
      assertThat(call(client, fraud, "http://127.0.0.1:" + receiver.getAddress().getPort() + "/push"))
          .isEqualTo("TASK_STATE_SUBMITTED");
      org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10))
          .untilAsserted(() -> { assertThat(delivered).isTrue(); fraud.verifyPush(); });
      fraud.verifyOneSend();
      fraud.verifyAuthenticated();
    } finally {
      receiver.stop(0);
    }
  }

  private String call(HttpClient client, FraudEndpoint fraud, String pushUrl) throws Exception {
    var request = HttpRequest.newBuilder(URI.create(fraud.baseUrl() + "/a2a"))
        .header("Content-Type", "application/json")
        .header("Authorization", String.join(" ", "Bearer", "synthetic-unit-token"))
        .POST(HttpRequest.BodyPublishers.ofString(Demo.JSON.writeValueAsString(
            Map.of("jsonrpc", "2.0", "id", "test", "method", "SendMessage", "params",
                Map.of("configuration", Map.of("taskPushNotificationConfig",
                    Map.of("url", pushUrl, "token", "synthetic-unit-token")))))))
        .build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return Demo.JSON.readTree(response.body()).path("result").path("task").path("status").path("state").asText();
  }
}
