/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.mixpanel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.generator.dsl.ConfigurationProperty;
import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeTaskDefinition;
import io.camunda.connector.generator.dsl.PropertyCondition.IsEmpty;
import io.camunda.connector.generator.java.json.ElementTemplateModule;
import io.camunda.connector.generator.openapi.OpenApiOutboundTemplateGenerator;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Guards the committed template against drift (nothing in the build reruns {@link
 * GenerateElementTemplate}) and pins the curation decisions that the raw OpenAPI output gets wrong.
 */
class GenerateElementTemplateTest {

  private static ElementTemplate template;

  @BeforeAll
  static void generateTemplate() throws Exception {
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
            "element-templates/mixpanel-connector.json is stale -- rerun `mvn -pl"
                + " connectors/mixpanel test-compile exec:java"
                + " -Dexec.mainClass=io.camunda.connector.mixpanel.GenerateElementTemplate"
                + " -Dexec.classpathScope=test` and commit the result, bumping TEMPLATE_VERSION"
                + " and archiving the previous version first if that version has been released")
        .isEqualTo(committed);
  }

  @Test
  void runsAsHttpJson() {
    assertThat(template.properties())
        .filteredOn(p -> p.getBinding() instanceof ZeebeTaskDefinition)
        .extracting(Property::getValue)
        .contains("io.camunda:http-json:1");
    assertThat(template.engines().camunda()).isEqualTo("^8.11");
  }

  @Test
  void offersExactlyTheCuratedOperationsWithAStepAndPresetEach() {
    DropdownProperty operation = (DropdownProperty) property("operationId");

    assertThat(operation.getChoices())
        .extracting(DropdownChoice::value)
        .containsExactlyElementsOf(GenerateElementTemplate.OPERATION_IDS);
    assertThat(operation.getChoices())
        .extracting(DropdownChoice::name)
        .noneMatch(name -> name.startsWith("GET ") || name.startsWith("POST "));
    assertThat(template.presets())
        .extracting(p -> p.properties().get("operationId"))
        .containsExactlyElementsOf(GenerateElementTemplate.OPERATION_IDS);
  }

  @Test
  void offersAReusableServiceAccountCredential() {
    assertThat(template.configurationTemplates())
        .singleElement()
        .satisfies(credential -> {
          assertThat(credential.id()).isEqualTo(GenerateElementTemplate.CREDENTIAL_ID);
          assertThat(credential.properties())
              .filteredOn(HiddenProperty.class::isInstance)
              .singleElement()
              .satisfies(p -> {
                assertThat(p.getId()).isEqualTo("authentication.type");
                assertThat(p.getValue()).isEqualTo("basic");
              });
          assertThat(credential.properties())
              .filteredOn(p -> "authentication.password".equals(p.getId()))
              .singleElement()
              .satisfies(p -> assertThat(p.getSecret()).isTrue());
        });

    assertThat(property(GenerateElementTemplate.CREDENTIAL_CHOOSER_ID))
        .isInstanceOfSatisfying(
            ConfigurationProperty.class,
            chooser ->
                assertThat(chooser.getConfigurationTemplate())
                    .isEqualTo(GenerateElementTemplate.CREDENTIAL_ID));
  }

  @Test
  void hidesTheInlineServiceAccountOnceACredentialIsBound() {
    List<Property> authentication =
        template.properties().stream().filter(p -> "authentication".equals(p.getGroup())).toList();

    assertThat(authentication.getFirst().getId())
        .isEqualTo(GenerateElementTemplate.CREDENTIAL_CHOOSER_ID);
    assertThat(authentication.subList(1, authentication.size()))
        .extracting(Property::getId)
        .containsExactly(
            "authentication.type", "authentication.username", "authentication.password");
    assertThat(authentication.subList(1, authentication.size()))
        .allSatisfy(p ->
            assertThat(p.getCondition())
                .isEqualTo(new IsEmpty(GenerateElementTemplate.CREDENTIAL_CHOOSER_ID, true)));
    assertThat(property("authentication.password").getSecret()).isTrue();
  }

  @Test
  void dataResidencyPicksEachOperationsHost() {
    assertThat(((DropdownProperty) property("region")).getChoices())
        .extracting(DropdownChoice::value)
        .containsExactly("us", "eu", "in");
    assertThat((String) property("import-events_baseUrl").getValue())
        .contains("\"https://api.mixpanel.com\"", "\"https://api-eu.mixpanel.com\"");
    assertThat((String) property("insights-query_baseUrl").getValue())
        .contains("\"https://mixpanel.com/api/query\"", "\"https://eu.mixpanel.com/api/query\"");
  }

  @Test
  void everyCallSendsTheProjectId() {
    assertThat(property("projectId").getConstraints().notEmpty()).isTrue();
    assertThat(template.properties())
        .filteredOn(p -> new ZeebeInput("queryParameters").equals(p.getBinding()))
        .hasSize(GenerateElementTemplate.OPERATION_IDS.size())
        .allSatisfy(p -> assertThat((String) p.getValue()).contains("project_id: projectId"));
    assertThat(template.properties())
        .extracting(Property::getId)
        .noneMatch(id -> id != null && (id.contains("_query_") || id.contains("_header_")));
  }

  @Test
  void importEventIsStrictAndDerivesTimeAndInsertIdOnce() {
    assertThat(property("import-events_$queryParameters").getValue())
        .isEqualTo("={project_id: projectId, strict: \"1\"}");
    assertThat(property("import-events_eventTime").getValue())
        .isEqualTo(GenerateElementTemplate.EVENT_TIME);
    assertThat((String) property("import-events_body").getValue())
        .contains("time: eventTime", "\"$insert_id\": uuid()", "distinct_id: distinctId");
  }

  @Test
  void hiddenInputsComeAfterTheFieldsTheyRead() {
    // Zeebe applies input mappings in order: a mapping placed before its inputs would read nulls.
    List<String> ids = template.properties().stream().map(Property::getId).toList();

    assertThat(ids.indexOf("region")).isLessThan(ids.indexOf("import-events_baseUrl"));
    assertThat(ids.indexOf("import-events_eventTime"))
        .isLessThan(ids.indexOf("import-events_body"));
    assertThat(ids.indexOf("import-events_eventProperties"))
        .isLessThan(ids.indexOf("import-events_body"));
    assertThat(ids.indexOf("insights-query_reportId"))
        .isLessThan(ids.indexOf("insights-query_$queryParameters"));
    assertThat(ids.indexOf("import-events_baseUrl")).isLessThan(ids.indexOf("url"));
  }

  @Test
  void overlayKeepsOnlyServiceAccountAuthenticationAndNoHeaderFields() throws Exception {
    JsonNode spec = GenerateElementTemplate.patchedSpec();

    assertThat(spec.findValues("security"))
        .isNotEmpty()
        .allSatisfy(
            security -> assertThat(security.toString()).isEqualTo("[{\"ServiceAccount\":[]}]"));
    assertThat(spec.findValues("in")).extracting(JsonNode::asText).doesNotContain("header");
  }

  @Test
  void openApiGeneratorSupportsEveryCuratedOperation() throws Exception {
    var source = GenerateElementTemplate.generationSource();

    assertThat(new OpenApiOutboundTemplateGenerator(false).operations(source))
        .hasSize(GenerateElementTemplate.OPERATION_IDS.size())
        .allSatisfy(op -> assertThat(op.supported()).as(op.id()).isTrue());
  }

  private static Property property(String id) {
    return template.properties().stream()
        .filter(p -> Objects.equals(id, p.getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No property " + id));
  }
}
