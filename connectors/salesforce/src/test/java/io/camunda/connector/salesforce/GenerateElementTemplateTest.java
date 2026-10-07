/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.salesforce;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.generator.dsl.ConfigurationProperty;
import io.camunda.connector.generator.dsl.ConfigurationTemplate;
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.dsl.PropertyCondition.AllMatch;
import io.camunda.connector.generator.dsl.PropertyCondition.IsEmpty;
import io.camunda.connector.generator.java.json.ElementTemplateModule;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Guards against Salesforce silently drifting from HTTP JSON's auth model: {@link
 * GenerateElementTemplate} isn't wired into the Maven build (unlike connectors that use {@code
 * element-template-generator-maven-plugin}), so nothing previously re-ran it to catch a stale
 * committed {@code element-templates/salesforce-connector.json} after an HTTP JSON change.
 */
class GenerateElementTemplateTest {

  private static final String REST_AUTHENTICATION_TEMPLATE_ID =
      "io.camunda.connectors:rest-authentication:1";

  private static ElementTemplate template;

  @BeforeAll
  static void generateTemplate() {
    template = GenerateElementTemplate.generate();
  }

  @Test
  void generatedTemplateMatchesCommittedJson() throws Exception {
    ObjectMapper mapper = new ObjectMapper().registerModule(new ElementTemplateModule());

    JsonNode generated = mapper.readTree(mapper.writeValueAsString(template));
    JsonNode committed =
        mapper.readTree(Files.readString(GenerateElementTemplate.templateOutputPath()));

    assertThat(generated)
        .as(
            "element-templates/salesforce-connector.json is stale -- rerun `mvn -pl"
                + " connectors/salesforce test-compile exec:java"
                + " -Dexec.mainClass=io.camunda.connector.salesforce.GenerateElementTemplate"
                + " -Dexec.classpathScope=test` and commit the result, bumping TEMPLATE_VERSION"
                + " and archiving the previous version first if that version has been released")
        .isEqualTo(committed);
  }

  @Test
  void offersReusableRestAuthenticationCredential() {
    assertThat(template.configurationTemplates())
        .extracting(ConfigurationTemplate::id)
        .containsExactly(REST_AUTHENTICATION_TEMPLATE_ID);
    assertThat(authenticationProperties(template).get(0))
        .isInstanceOfSatisfying(
            ConfigurationProperty.class,
            chooser -> {
              assertThat(chooser.getId()).isEqualTo("authenticationConfiguration");
              assertThat(chooser.getConfigurationTemplate())
                  .isEqualTo(REST_AUTHENTICATION_TEMPLATE_ID);
            });
    assertThat(template.engines().camunda()).isEqualTo("^8.11");
  }

  @Test
  void hidesEveryInlineAuthenticationFieldOnceACredentialIsBound() {
    List<Property> inlineAuthProperties =
        authenticationProperties(template).stream()
            .filter(p -> !(p instanceof ConfigurationProperty))
            .toList();

    assertThat(inlineAuthProperties)
        .isNotEmpty()
        .allSatisfy(
            p ->
                assertThat(hidesWhenCredentialBound(p.getCondition()))
                    .as(
                        "property %s is gated on authenticationConfiguration being empty",
                        p.getId())
                    .isTrue());
  }

  private static List<Property> authenticationProperties(ElementTemplate generated) {
    return generated.properties().stream()
        .filter(p -> "authentication".equals(p.getGroup()))
        .toList();
  }

  private static boolean hidesWhenCredentialBound(PropertyCondition condition) {
    if (condition instanceof IsEmpty isEmpty) {
      return "authenticationConfiguration".equals(isEmpty.property()) && isEmpty.isEmpty();
    }
    if (condition instanceof AllMatch allMatch) {
      return allMatch.allMatch().stream()
          .anyMatch(GenerateElementTemplateTest::hidesWhenCredentialBound);
    }
    return false;
  }
}
