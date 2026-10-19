/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.SpanExporters;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** TMP: covers {@link TmpAgentTracingPocConfiguration}. Drop together with it. */
class TmpAgentTracingPocConfigurationTest {

  private static final String LANGFUSE_AUTHORIZATION =
      "Basic cGstbGYtY2FtdW5kYS1wb2M6c2stbGYtY2FtdW5kYS1wb2M=";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  OpenTelemetrySdkAutoConfiguration.class,
                  OpenTelemetryTracingAutoConfiguration.class,
                  OtlpTracingAutoConfiguration.class))
          .withUserConfiguration(TmpAgentTracingPocConfiguration.class)
          .withPropertyValues(
              "spring.profiles.active=local", "management.tracing.sampling.probability=1.0");

  private HttpServer phoenix;
  private HttpServer langfuse;
  private final Map<String, List<Request>> requests = new ConcurrentHashMap<>();

  private record Request(String path, String contentType, String authorization, String ingestion) {}

  @BeforeEach
  void startBackends() throws IOException {
    phoenix = startBackend("phoenix");
    langfuse = startBackend("langfuse");
  }

  @AfterEach
  void stopBackends() {
    phoenix.stop(0);
    langfuse.stop(0);
  }

  @Test
  void exportsToBothBackendsByDefault() {
    runner
        .withPropertyValues(endpoints())
        .run(
            context -> {
              assertThat(context.getBeansOfType(OtlpHttpSpanExporter.class))
                  .containsOnlyKeys("phoenixSpanExporter", "langfuseSpanExporter");
              assertThat(context.getBean(SpanExporters.class).list()).hasSize(2);
              assertThat(context).hasSingleBean(OpenTelemetry.class);

              exportOneSpan(context.getBean(OpenTelemetry.class));

              assertThat(requests.get("phoenix"))
                  .singleElement()
                  .satisfies(
                      request -> {
                        assertThat(request.path()).isEqualTo("/v1/traces");
                        assertThat(request.contentType()).isEqualTo("application/x-protobuf");
                        assertThat(request.authorization()).isNull();
                        assertThat(request.ingestion()).isNull();
                      });
              assertThat(requests.get("langfuse"))
                  .singleElement()
                  .satisfies(
                      request -> {
                        assertThat(request.path()).isEqualTo("/api/public/otel/v1/traces");
                        assertThat(request.contentType()).isEqualTo("application/x-protobuf");
                        assertThat(request.authorization()).isEqualTo(LANGFUSE_AUTHORIZATION);
                        assertThat(request.ingestion()).isEqualTo("4");
                      });
            });
  }

  @Test
  void bootsOwnOtlpExporterBacksOffEvenWhenItsEndpointIsSet() {
    runner
        .withPropertyValues(endpoints())
        .withPropertyValues(
            "management.opentelemetry.tracing.export.otlp.endpoint=http://127.0.0.1:1/ignored")
        .run(
            context ->
                assertThat(context.getBeansOfType(SpanExporter.class))
                    .containsOnlyKeys("phoenixSpanExporter", "langfuseSpanExporter"));
  }

  @Test
  void exportsOnlyToLangfuseWhenPhoenixIsSwitchedOff() {
    runner
        .withPropertyValues(endpoints())
        .withPropertyValues("agent-tracing-poc.phoenix.enabled=false")
        .run(
            context -> {
              assertThat(context.getBeansOfType(OtlpHttpSpanExporter.class))
                  .containsOnlyKeys("langfuseSpanExporter");
              assertThat(context.getBean(SpanExporters.class).list()).hasSize(1);
              assertThat(context).hasSingleBean(OpenTelemetry.class);

              exportOneSpan(context.getBean(OpenTelemetry.class));

              assertThat(requests.get("phoenix")).isNull();
              assertThat(requests.get("langfuse")).hasSize(1);
            });
  }

  @Test
  void exportsOnlyToPhoenixWhenLangfuseIsSwitchedOff() {
    runner
        .withPropertyValues(endpoints())
        .withPropertyValues("agent-tracing-poc.langfuse.enabled=false")
        .run(
            context -> {
              assertThat(context.getBeansOfType(OtlpHttpSpanExporter.class))
                  .containsOnlyKeys("phoenixSpanExporter");
              assertThat(context.getBean(SpanExporters.class).list()).hasSize(1);

              exportOneSpan(context.getBean(OpenTelemetry.class));

              assertThat(requests.get("phoenix")).hasSize(1);
              assertThat(requests.get("langfuse")).isNull();
            });
  }

  @Test
  void keepsTheOpenTelemetryBeanWithoutAnyExporter() {
    runner
        .withPropertyValues(
            "agent-tracing-poc.phoenix.enabled=false", "agent-tracing-poc.langfuse.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class);
              assertThat(context.getBean(SpanExporters.class).list()).isEmpty();
              assertThat(context).hasSingleBean(OpenTelemetry.class);
            });
  }

  @Test
  void aBackendThatIsNotRunningDoesNotBreakTheOtherOne() {
    int closedPort = unusedPort();
    runner
        .withPropertyValues(
            "agent-tracing-poc.phoenix.endpoint=http://127.0.0.1:" + closedPort + "/v1/traces",
            "agent-tracing-poc.langfuse.endpoint=http://127.0.0.1:"
                + langfuse.getAddress().getPort()
                + "/api/public/otel/v1/traces")
        .run(
            context -> {
              assertThat(context).hasNotFailed();

              exportOneSpan(context.getBean(OpenTelemetry.class));

              assertThat(requests.get("langfuse")).hasSize(1);
            });
  }

  @Test
  void doesNothingWithoutTheLocalProfile() {
    new ApplicationContextRunner()
        .withUserConfiguration(TmpAgentTracingPocConfiguration.class)
        .run(context -> assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class));
  }

  private String[] endpoints() {
    return new String[] {
      "agent-tracing-poc.phoenix.endpoint=http://127.0.0.1:"
          + phoenix.getAddress().getPort()
          + "/v1/traces",
      "agent-tracing-poc.langfuse.endpoint=http://127.0.0.1:"
          + langfuse.getAddress().getPort()
          + "/api/public/otel/v1/traces"
    };
  }

  private static void exportOneSpan(OpenTelemetry openTelemetry) {
    openTelemetry.getTracer("test").spanBuilder("invoke_agent test").startSpan().end();
    var flushed =
        ((OpenTelemetrySdk) openTelemetry)
            .getSdkTracerProvider()
            .forceFlush()
            .join(10, TimeUnit.SECONDS);
    assertThat(flushed.isDone()).isTrue();
  }

  private HttpServer startBackend(String name) throws IOException {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          requests
              .computeIfAbsent(name, key -> new CopyOnWriteArrayList<>())
              .add(
                  new Request(
                      exchange.getRequestURI().getPath(),
                      exchange.getRequestHeaders().getFirst("Content-Type"),
                      exchange.getRequestHeaders().getFirst("Authorization"),
                      exchange.getRequestHeaders().getFirst("x-langfuse-ingestion-version")));
          exchange.getRequestBody().readAllBytes();
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();
    return server;
  }

  private static int unusedPort() {
    try (var socket = new java.net.ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
