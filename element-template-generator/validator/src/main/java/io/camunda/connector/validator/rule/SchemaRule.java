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
package io.camunda.connector.validator.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.camunda.connector.validator.core.Finding;
import io.camunda.connector.validator.core.Rule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Validates each template against the upstream Camunda element-template JSON schema.
 *
 * <p>Schema is fetched once at construction from {@link #SCHEMA_URL}. If the fetch fails the
 * constructor throws — failing loudly is preferable to silently skipping schema validation.
 */
public class SchemaRule implements Rule {

  public static final String ID = "schema";

  /**
   * Pinned schema version. Bump deliberately (in lockstep with the connectors-team upgrade) rather
   * than tracking {@code latest}, so validator behavior is reproducible across CI runs and local
   * machines. Override at runtime via {@code --schema-url} or {@code CAMUNDA_TEMPLATE_SCHEMA_URL}.
   */
  public static final String SCHEMA_VERSION = "0.40.0";

  public static final String SCHEMA_URL =
      "https://unpkg.com/@camunda/zeebe-element-templates-json-schema@"
          + SCHEMA_VERSION
          + "/resources/schema.json";

  private final JsonSchema schema;

  public SchemaRule() {
    this(SCHEMA_URL);
  }

  public SchemaRule(String schemaUrl) {
    this.schema = loadSchema(schemaUrl);
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public List<Finding> apply(Path file, JsonNode template) {
    Set<ValidationMessage> messages = schema.validate(template);
    return messages.stream()
        .map(m -> Finding.error(file, m.getInstanceLocation().toString(), ID, m.getMessage()))
        .toList();
  }

  private static JsonSchema loadSchema(String url) {
    try {
      HttpClient client =
          HttpClient.newBuilder()
              .followRedirects(HttpClient.Redirect.NORMAL)
              .connectTimeout(Duration.ofSeconds(10))
              .build();
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        throw new IllegalStateException(
            "Failed to fetch schema from " + url + ": HTTP " + response.statusCode());
      }
      ObjectMapper mapper = new ObjectMapper();
      JsonNode schemaNode = mapper.readTree(response.body());
      JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
      SchemaValidatorsConfig config = new SchemaValidatorsConfig();
      return factory.getSchema(schemaNode, config);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to fetch element-template schema from " + url, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while fetching schema", e);
    }
  }
}
