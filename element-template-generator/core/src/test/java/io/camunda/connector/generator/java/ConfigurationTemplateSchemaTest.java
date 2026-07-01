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
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ConfigurationTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.json.ElementTemplateModule;
import java.io.InputStream;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Validates the configuration templates emitted by the generator against the official pre-release
 * <a
 * href="https://github.com/camunda/element-templates-json-schema">{@code
 * @camunda/zeebe-configuration-templates-json-schema}</a> (draft-07). The schema resource is a
 * verbatim copy of {@code resources/schema.json} from
 * {@code @camunda/zeebe-configuration-templates-json-schema@0.2.0-alpha.0} (bpmn-io/internal-docs
 * #1331), vendored at {@code src/test/resources/configuration-template-schema.json} so the test
 * stays hermetic.
 */
public class ConfigurationTemplateSchemaTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new ElementTemplateModule());

  private final ClassBasedTemplateGenerator generator = new ClassBasedTemplateGenerator();

  private static JsonSchema loadSchema() {
    try (InputStream is =
        ConfigurationTemplateSchemaTest.class
            .getClassLoader()
            .getResourceAsStream("configuration-template-schema.json")) {
      var schemaNode = MAPPER.readTree(is);
      return JsonSchemaFactory.getInstance(VersionFlag.V7).getSchema(schemaNode);
    } catch (Exception e) {
      throw new RuntimeException("Failed to load configuration-template schema", e);
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

  @ConfigurationTemplate(id = "io.camunda:jdbc-credential:1", version = 1, name = "JDBC Connection")
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
      configurationTemplates = {JdbcConnection.class})
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

  @ConfigurationTemplate(
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
      configurationTemplates = {AwsCredential.class})
  static class AwsConnector implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }
}
