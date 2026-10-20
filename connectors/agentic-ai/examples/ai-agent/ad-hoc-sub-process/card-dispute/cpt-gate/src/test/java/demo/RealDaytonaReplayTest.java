package demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "gate.replayFile", matches = ".+")
class RealDaytonaReplayTest {
  @Test void capturedRunSatisfiesCollectorWithoutCertifyingIt() throws Exception {
    var captured = Demo.JSON.readTree(Files.readString(Path.of(System.getProperty("gate.replayFile"))));
    var allocation = captured.path("allocation");
    long owner = Long.parseLong(allocation.path("owner").asText());
    var cleaned = new AtomicBoolean(false);
    var cleanup = new SandboxCleanup((method, url, query) -> {
      assertThat(method).isEqualTo("GET");
      if (url.endsWith("/sandbox")) {
        return Demo.JSON.valueToTree(Map.of("items", cleaned.get() ? java.util.List.of() :
            java.util.List.of(Map.of("id", allocation.path("id").asText(), "owner", Long.toString(owner)))));
      }
      assertThat(url).endsWith("/sandbox/" + allocation.path("id").asText());
      return allocation;
    });
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      var body = switch (exchange.getRequestURI().getPath()) {
        case "/v2/variables/search" -> captured.path("variables");
        case "/v2/user-tasks/search" -> Demo.JSON.valueToTree(Map.of("items", java.util.List.of()));
        default -> {
          assertThat(exchange.getRequestURI().getPath()).isEqualTo("/v2/process-instances/" + owner);
          yield captured.path("instance");
        }
      };
      byte[] bytes = Demo.JSON.writeValueAsBytes(body);
      exchange.sendResponseHeaders(200, bytes.length);
      try (var response = exchange.getResponseBody()) {
        response.write(bytes);
      }
    });
    server.start();
    try {
      var api = new Api("http://127.0.0.1:" + server.getAddress().getPort(), false);
      var collector = new RealDaytonaEvidence(api, cleanup, owner);
      collector.observe();
      cleaned.set(true);
      var execution = collector.execution(captured.path("instance").path("processDefinitionKey").asText());
      assertThat(execution.get("transactionDocumentId")).isEqualTo("89d8781e-71f6-4cc7-8bc1-cd0fb8fa8d2b");
      assertThat(execution.get("sandboxCreateCount")).isEqualTo(1);
      Files.writeString(Path.of("target/replayed-execution.json"), Demo.JSON.writeValueAsString(execution));
    } finally {
      server.stop(0);
    }
  }
}
