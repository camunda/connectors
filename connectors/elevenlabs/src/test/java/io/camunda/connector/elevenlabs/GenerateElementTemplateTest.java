/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.elevenlabs;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.generator.dsl.ConfigurationProperty;
import io.camunda.connector.generator.dsl.ConfigurationTemplate;
import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeTaskDefinition;
import io.camunda.connector.generator.dsl.PropertyCondition.Equals;
import io.camunda.connector.generator.dsl.PropertyCondition.IsEmpty;
import io.camunda.connector.generator.java.json.ElementTemplateModule;
import io.camunda.connector.generator.openapi.OpenApiOutboundTemplateGenerator;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
            "element-templates/elevenlabs-connector.json is stale -- rerun `mvn -pl"
                + " connectors/elevenlabs test-compile exec:java"
                + " -Dexec.mainClass=io.camunda.connector.elevenlabs.GenerateElementTemplate"
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
  void offersExactlyTheCuratedOperations() {
    DropdownProperty operation = (DropdownProperty) property("operationId");

    assertThat(operation.getChoices())
        .extracting(DropdownChoice::value)
        .containsExactlyElementsOf(GenerateElementTemplate.OPERATION_IDS);
    assertThat(operation.getChoices())
        .extracting(DropdownChoice::name)
        .noneMatch(name -> name.startsWith("GET ") || name.startsWith("POST "));
    assertThat(GenerateElementTemplate.OPERATION_IDS).hasSizeLessThanOrEqualTo(20);
  }

  @Test
  void everyCuratedOperationHasAStepAndPreset() {
    assertThat(template.presets())
        .extracting(p -> p.properties().get("operationId"))
        .containsExactlyElementsOf(GenerateElementTemplate.OPERATION_IDS);
  }

  @Test
  void offersReusableElevenLabsCredential() {
    assertThat(template.configurationTemplates())
        .singleElement()
        .satisfies(credential -> {
          assertThat(credential.id()).isEqualTo(GenerateElementTemplate.CREDENTIAL_ID);
          assertThat(constants(credential))
              .containsExactlyInAnyOrderEntriesOf(
                  Map.of(
                      "authentication.type", "apiKey",
                      "authentication.apiKeyLocation", "headers",
                      "authentication.name", "xi-api-key"));
          assertThat(credential.properties())
              .filteredOn(p -> "authentication.value".equals(p.getId()))
              .singleElement()
              .satisfies(p -> assertThat(p.getSecret()).isTrue());
        });

    assertThat(property(GenerateElementTemplate.CREDENTIAL_CHOOSER_ID))
        .isInstanceOfSatisfying(
            ConfigurationProperty.class,
            chooser -> {
              assertThat(chooser.getConfigurationTemplate())
                  .isEqualTo(GenerateElementTemplate.CREDENTIAL_ID);
              assertThat(chooser.getBinding())
                  .isEqualTo(new ZeebeInput("serviceAuthenticationConfiguration"));
            });
  }

  @Test
  void hidesInlineAuthenticationOnceACredentialIsBound() {
    List<Property> authentication =
        template.properties().stream().filter(p -> "authentication".equals(p.getGroup())).toList();

    assertThat(authentication.getFirst().getId())
        .isEqualTo(GenerateElementTemplate.CREDENTIAL_CHOOSER_ID);
    assertThat(authentication.subList(1, authentication.size()))
        .isNotEmpty()
        .allSatisfy(p ->
            assertThat(p.getCondition())
                .isEqualTo(new IsEmpty(GenerateElementTemplate.CREDENTIAL_CHOOSER_ID, true)));
    assertThat(property("authentication.name.xi-api-key").getValue()).isEqualTo("xi-api-key");
    assertThat(property("authentication.value.xi-api-key").getSecret()).isTrue();
  }

  @Test
  void textToSpeechDefaultsToDocumentReference() {
    Property format = property("documentReturnFormat");

    assertThat(format.getValue()).isEqualTo("DOCUMENT");
    assertThat(format.getCondition()).isEqualTo(new Equals("operationId", "text_to_speech_full"));
  }

  @Test
  void doesNotRetryByDefault() {
    assertThat(property("retryCount").getValue()).isEqualTo("0");
  }

  @Test
  void parametersHaveNoExampleValuesAsDefaults() {
    assertThat(template.properties())
        .filteredOn(p -> "parameters".equals(p.getGroup()))
        .filteredOn(p -> !(p instanceof HiddenProperty))
        .allSatisfy(p -> {
          assertThat(p.getValue()).as("default value of %s", p.getId()).isNull();
          assertThat(p.getLabel()).as("label of %s", p.getId()).doesNotContain("_");
        });
  }

  @Test
  void dropsUnsetQueryParameters() {
    assertThat(template.properties())
        .filteredOn(p -> new ZeebeInput("queryParameters").equals(p.getBinding()))
        .extracting(p -> (String) p.getValue())
        .allSatisfy(value ->
            assertThat(value)
                .satisfiesAnyOf(
                    v -> assertThat(v).isEqualTo("={}"),
                    v ->
                        assertThat(v)
                            .startsWith("=context(get entries({")
                            .endsWith("})[value != null])")));
  }

  @Test
  void everyRequestBodyHasAnExampleAndIsRequired() {
    assertThat(template.properties())
        .filteredOn(p -> "requestBody".equals(p.getGroup()))
        .hasSize(3)
        .allSatisfy(p -> {
          assertThat(p.getPlaceholder()).startsWith("={");
          assertThat(p.getConstraints().notEmpty()).isTrue();
        });
  }

  @Test
  void openApiGeneratorSupportsEveryCuratedOperation() throws Exception {
    var source = GenerateElementTemplate.generationSource();

    assertThat(new OpenApiOutboundTemplateGenerator(false).operations(source))
        .hasSize(GenerateElementTemplate.OPERATION_IDS.size())
        .allSatisfy(op -> assertThat(op.supported()).as(op.id()).isTrue());
  }

  @ParameterizedTest
  @CsvSource({
    "Voice_id, Voice ID",
    "Output_format, Output format",
    "voice_ids, Voice IDs",
    "Search, Search",
    "min_notice_period_days, Min notice period days"
  })
  void humanizesParameterLabels(String raw, String expected) {
    assertThat(GenerateElementTemplate.humanize(raw)).isEqualTo(expected);
  }

  private static Property property(String id) {
    return template.properties().stream()
        .filter(p -> Objects.equals(id, p.getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No property " + id));
  }

  private static Map<String, Object> constants(ConfigurationTemplate credential) {
    return credential.properties().stream()
        .filter(HiddenProperty.class::isInstance)
        .collect(java.util.stream.Collectors.toMap(Property::getId, Property::getValue));
  }
}
