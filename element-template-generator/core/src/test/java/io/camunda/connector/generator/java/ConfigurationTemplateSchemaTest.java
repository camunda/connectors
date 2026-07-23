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
package io.camunda.connector.generator.java;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.ValidationMessage;
import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.json.ElementTemplateModule;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Validates the configuration templates emitted by the generator against the official <a
 * href="https://github.com/camunda/element-templates-json-schema">{@code
 * @camunda/zeebe-configuration-templates-json-schema}</a> (draft-07), fetched from the npm registry
 * (unpkg) rather than vendored in the repo. Pinned to a released version so behavior is reproducible
 * across CI runs and local machines; bump {@link #SCHEMA_VERSION} deliberately.
 */
public class ConfigurationTemplateSchemaTest {

  /**
   * Pinned released schema version; bump deliberately in lockstep with the connectors-team upgrade.
   */
  private static final String SCHEMA_VERSION = "0.2.0";

  private static final String SCHEMA_URL =
      "https://unpkg.com/@camunda/zeebe-configuration-templates-json-schema@"
          + SCHEMA_VERSION
          + "/resources/schema.json";

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new ElementTemplateModule());

  private final ClassBasedTemplateGenerator generator = new ClassBasedTemplateGenerator();

  private static JsonSchema loadSchema() {
    try {
      HttpClient client =
          HttpClient.newBuilder()
              .followRedirects(HttpClient.Redirect.NORMAL)
              .connectTimeout(Duration.ofSeconds(10))
              .build();
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(SCHEMA_URL))
              .timeout(Duration.ofSeconds(20))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        throw new IllegalStateException(
            "Failed to fetch configuration-template schema from "
                + SCHEMA_URL
                + ": HTTP "
                + response.statusCode());
      }
      var schemaNode = MAPPER.readTree(response.body());
      return JsonSchemaFactory.getInstance(VersionFlag.V7).getSchema(schemaNode);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while fetching configuration-template schema", e);
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to load configuration-template schema from " + SCHEMA_URL, e);
    }
  }

  private Set<ValidationMessage> validateConfigurationTemplates(Class<?> connector) {
    var template = generator.generate(connector).getFirst();
    JsonNode configurationTemplates = MAPPER.valueToTree(template.configurationTemplates());
    return loadSchema().validate(configurationTemplates);
  }

  @Test
  void jdbcConfigurationTemplate_conformsToSchema() {
    assertThat(validateConfigurationTemplates(JdbcConnector.class)).isEmpty();
  }

  @Test
  void awsConfigurationTemplate_conformsToSchema() {
    assertThat(validateConfigurationTemplates(AwsConnector.class)).isEmpty();
  }

  // --- JDBC: flat whole-object configuration ---

  @Configuration(id = "io.camunda:jdbc-credential:1", version = 1, name = "JDBC Connection")
  record JdbcConnection(String url, String username, String password) {}

  record JdbcRequest(
      @TemplateProperty(
              type = TemplateProperty.PropertyType.Configuration,
              group = "connection",
              binding = @TemplateProperty.PropertyBinding(name = "configuration"))
          JdbcConnection configuration) {}

  @OutboundConnector(name = "JDBC", type = "test:jdbc-schema")
  @ElementTemplate(
      id = "test-jdbc-schema",
      name = "JDBC",
      version = 1,
      inputDataClass = JdbcRequest.class,
      configurations = {JdbcConnection.class})
  static class JdbcConnector implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }

  // --- AWS: nested value shape, secret hints, dropdown + condition, explicit kind ---

  record AwsAuthentication(
      @TemplateProperty(
              type = TemplateProperty.PropertyType.Dropdown,
              choices = {
                @TemplateProperty.DropdownPropertyChoice(
                    label = "Default chain",
                    value = "defaultCredentialsChain"),
                @TemplateProperty.DropdownPropertyChoice(
                    label = "Access key",
                    value = "credentials")
              })
          String type,
      @TemplateProperty(secret = true, optional = true) String accessKey,
      @TemplateProperty(secret = true, optional = true) String secretKey) {}

  @Configuration(
      id = "io.camunda:aws-credential:1",
      version = 2,
      name = "AWS Credential",
      kind = "CREDENTIAL")
  record AwsCredential(
      @TemplateProperty(group = "authentication") AwsAuthentication authentication,
      @TemplateProperty(group = "connection") String region) {}

  record AwsRequest(
      @TemplateProperty(
              type = TemplateProperty.PropertyType.Configuration,
              group = "authentication",
              binding = @TemplateProperty.PropertyBinding(name = "configuration"))
          AwsCredential configuration) {}

  @OutboundConnector(name = "AWS", type = "test:aws-schema")
  @ElementTemplate(
      id = "test-aws-schema",
      name = "AWS",
      version = 1,
      inputDataClass = AwsRequest.class,
      configurations = {AwsCredential.class})
  static class AwsConnector implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }
}
