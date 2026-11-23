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

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * TMP: agent tracing PoC. Drop before merging, together with the {@code agent-tracing-poc.*}
 * properties of {@code application-local.properties}.
 *
 * <p>Sends the traces of the local runtime to the local Arize Phoenix and Langfuse backends of
 * {@code connectors/agentic-ai/examples/tracing} at the same time. Spring Boot's OTLP properties
 * describe a single exporter, so both exporters are defined here. Boot collects every {@code
 * SpanExporter} bean into one composite exporter behind its batch span processor, and its own OTLP
 * exporter backs off because these beans are {@link OtlpHttpSpanExporter}s. Boot's own {@code
 * management.opentelemetry.tracing.export.otlp.*} properties are therefore not used.
 *
 * <p>A backend that is not running only produces throttled exporter log lines. The exporters do not
 * retry, so that a batch for a backend that is down does not hold up the batch span processor for
 * the other one. Set {@code agent-tracing-poc.phoenix.enabled=false} or {@code
 * agent-tracing-poc.langfuse.enabled=false} to leave one out.
 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
public class TmpAgentTracingPocConfiguration {

  @Bean
  @ConditionalOnProperty(
      name = "agent-tracing-poc.phoenix.enabled",
      havingValue = "true",
      matchIfMissing = true)
  OtlpHttpSpanExporter phoenixSpanExporter(
      @Value("${agent-tracing-poc.phoenix.endpoint:http://localhost:6006/v1/traces}")
          String endpoint) {
    return OtlpHttpSpanExporter.builder().setEndpoint(endpoint).setRetryPolicy(null).build();
  }

  @Bean
  @ConditionalOnProperty(
      name = "agent-tracing-poc.langfuse.enabled",
      havingValue = "true",
      matchIfMissing = true)
  OtlpHttpSpanExporter langfuseSpanExporter(
      @Value(
              "${agent-tracing-poc.langfuse.endpoint:http://localhost:3000/api/public/otel/v1/traces}")
          String endpoint,
      // base64("pk-lf-camunda-poc:sk-lf-camunda-poc"), the local-only keys seeded by the compose
      // file in connectors/agentic-ai/examples/tracing
      @Value(
              "${agent-tracing-poc.langfuse.authorization:Basic cGstbGYtY2FtdW5kYS1wb2M6c2stbGYtY2FtdW5kYS1wb2M=}")
          String authorization) {
    return OtlpHttpSpanExporter.builder()
        .setEndpoint(endpoint)
        .setRetryPolicy(null)
        .addHeader("Authorization", authorization)
        .addHeader("x-langfuse-ingestion-version", "4")
        .build();
  }
}
