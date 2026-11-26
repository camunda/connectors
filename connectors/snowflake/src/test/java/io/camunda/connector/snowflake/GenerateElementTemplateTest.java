/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.snowflake;

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
import io.camunda.connector.generator.dsl.PropertyCondition.Equals;
import io.camunda.connector.generator.dsl.PropertyCondition.IsEmpty;
import io.camunda.connector.generator.java.json.ElementTemplateModule;
import io.camunda.connector.generator.openapi.OpenApiOutboundTemplateGenerator;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
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
            "element-templates/snowflake-connector.json is stale -- rerun `mvn -pl"
                + " connectors/snowflake test-compile exec:java"
                + " -Dexec.mainClass=io.camunda.connector.snowflake.GenerateElementTemplate"
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
  void offersEverySqlApiOperation() {
    DropdownProperty operation = (DropdownProperty) property("operationId");

    assertThat(operation.getChoices())
        .extracting(DropdownChoice::value)
        .containsExactly("SubmitStatement", "GetStatementStatus", "CancelStatement");
    assertThat(operation.getChoices())
        .extracting(DropdownChoice::name)
        .containsExactly("Run SQL statement", "Get statement status", "Cancel statement");
    assertThat(template.presets())
        .extracting(p -> p.properties().get("operationId"))
        .containsExactlyElementsOf(GenerateElementTemplate.OPERATION_IDS);
  }

  @Test
  void openApiGeneratorSupportsEveryOperationOfTheSpec() throws Exception {
    var source = GenerateElementTemplate.generationSource();

    assertThat(new OpenApiOutboundTemplateGenerator(false).operations(source))
        .hasSize(GenerateElementTemplate.OPERATION_IDS.size())
        .allSatisfy(op -> assertThat(op.supported()).as(op.id()).isTrue());
    assertThat(GenerateElementTemplate.patchedSpec().at("/paths").size())
        .as("operations in the spec")
        .isEqualTo(GenerateElementTemplate.OPERATION_IDS.size());
  }

  @Test
  void offersReusableSnowflakeCredential() {
    assertThat(template.configurationTemplates())
        .singleElement()
        .satisfies(credential -> {
          assertThat(credential.id()).isEqualTo(GenerateElementTemplate.CREDENTIAL_ID);
          assertThat(credential.properties())
              .extracting(Property::getId)
              .containsExactly("authentication.type", "authentication.token");
          assertThat(credential.properties().getFirst())
              .isInstanceOf(HiddenProperty.class)
              .extracting(Property::getValue)
              .isEqualTo("bearer");
          assertThat(credential.properties().getLast().getSecret()).isTrue();
        });

    assertThat(property(GenerateElementTemplate.CREDENTIAL_CHOOSER_ID))
        .isInstanceOfSatisfying(
            ConfigurationProperty.class,
            chooser -> {
              assertThat(chooser.getConfigurationTemplate())
                  .isEqualTo(GenerateElementTemplate.CREDENTIAL_ID);
              assertThat(chooser.getBinding())
                  .isEqualTo(new ZeebeInput("serviceAuthenticationConfiguration"));
              // An empty chooser still maps its input to null, so a process variable of the
              // same name cannot replace the inline token.
              assertThat(chooser.getOptional()).isFalse();
              assertThat(chooser.getConstraints()).isNull();
            });
  }

  @Test
  void hidesInlineAuthenticationOnceACredentialIsBound() {
    List<Property> authentication =
        template.properties().stream()
            .filter(p -> "authentication".equals(p.getGroup()))
            .filter(p -> !GenerateElementTemplate.REST_CREDENTIAL_INPUT.equals(p.getId()))
            .toList();

    assertThat(authentication)
        .extracting(Property::getId)
        .startsWith("baseUrl", GenerateElementTemplate.CREDENTIAL_CHOOSER_ID);
    assertThat(authentication.getFirst().getCondition()).isNull();
    assertThat(authentication.subList(2, authentication.size()))
        .isNotEmpty()
        .allSatisfy(p ->
            assertThat(p.getCondition())
                .isEqualTo(new IsEmpty(GenerateElementTemplate.CREDENTIAL_CHOOSER_ID, true)));
    assertThat(property("bearer").getValue()).isEqualTo("bearer");
    assertThat(property("authentication.token").getSecret()).isTrue();
  }

  @Test
  void alwaysMapsTheRestConnectorsCredentialToNull() {
    assertThat(property(GenerateElementTemplate.REST_CREDENTIAL_INPUT))
        .isInstanceOf(HiddenProperty.class)
        .satisfies(p -> {
          assertThat(p.getValue()).isEqualTo("=null");
          assertThat(p.getBinding())
              .isEqualTo(new ZeebeInput(GenerateElementTemplate.REST_CREDENTIAL_INPUT));
          assertThat(p.getCondition()).isNull();
        });
  }

  @Test
  void neverSendsTheTokenTypeHeaderOrAsksForHeaders() {
    assertThat(template.properties())
        .extracting(Property::getId)
        .filteredOn(Objects::nonNull)
        .noneMatch(id -> id.contains("_header_"));
    assertThat(template.properties())
        .filteredOn(p -> new ZeebeInput("headers").equals(p.getBinding()))
        .hasSize(3)
        .allSatisfy(p -> {
          assertThat(p).isInstanceOf(HiddenProperty.class);
          assertThat(p.getValue()).isEqualTo(GenerateElementTemplate.HEADERS);
        });
    assertThat(GenerateElementTemplate.HEADERS).doesNotContain("X-Snowflake");
  }

  @ParameterizedTest
  @CsvSource({
    "https://myorg-myaccount.snowflakecomputing.com, true",
    "https://xy12345.eu-central-1.privatelink.snowflakecomputing.com, true",
    "=snowflakeAccountUrl, true",
    "https://myorg-myaccount.snowflakecomputing.com/, false",
    "https://myorg-myaccount.snowflakecomputing.com/api/v2, false",
    "https://myorg-myaccount.snowflakecomputing.com#account, false",
    "https://myorg-myaccount.snowflakecomputing.com?region=eu, false",
    "http://myorg-myaccount.snowflakecomputing.com, false",
    "myorg-myaccount, false"
  })
  void accountUrlIsARequiredHostWithoutAPath(String value, boolean valid) {
    Property accountUrl = property("baseUrl");

    assertThat(accountUrl.getValue()).isNull();
    assertThat(accountUrl.getConstraints().notEmpty()).isTrue();
    assertThat(Pattern.matches(accountUrl.getConstraints().pattern().value(), value))
        .isEqualTo(valid);
  }

  @Test
  void parametersHaveNoExampleValuesAsDefaults() {
    assertThat(template.properties())
        .filteredOn(p -> "parameters".equals(p.getGroup()))
        .filteredOn(p -> !(p instanceof HiddenProperty))
        .hasSize(8)
        .allSatisfy(p -> {
          assertThat(p.getTooltip()).as("tooltip of %s", p.getId()).isNotBlank();
          assertThat(p.getDescription()).as("description of %s", p.getId()).isNull();
          if (p instanceof DropdownProperty) {
            // Snowflake's own defaults, so a non-optional dropdown changes nothing.
            assertThat(p.getValue())
                .as("value of %s", p.getId())
                .isEqualTo(p.getId().endsWith("_async") ? "false" : "true");
          } else {
            assertThat(p.getValue()).as("default value of %s", p.getId()).isNull();
          }
        });
  }

  @Test
  void everyParameterAlwaysMapsItsInputSoProcessVariablesCannotLeakIn() {
    assertThat(template.properties())
        .filteredOn(p -> "parameters".equals(p.getGroup()))
        .filteredOn(p -> !(p instanceof HiddenProperty))
        .allSatisfy(p -> assertThat(p.getOptional()).as("optional of %s", p.getId()).isFalse());
  }

  @ParameterizedTest
  @CsvSource({
    "SubmitStatement_query_requestId, '', true",
    "SubmitStatement_query_requestId, 01b2c3d4-0000-1111-0000-000000000001, true",
    "SubmitStatement_query_requestId, =requestUuid, true",
    "SubmitStatement_query_requestId, not-a-uuid, false",
    "GetStatementStatus_query_partition, '', true",
    "GetStatementStatus_query_partition, 12, true",
    "GetStatementStatus_query_partition, =index, true",
    "GetStatementStatus_query_partition, first, false",
    "GetStatementStatus_query_partition, -1, false",
    "GetStatementStatus_path_statementHandle, 01b2c3d4-0000-1111-0000-000000000001, true",
    "GetStatementStatus_path_statementHandle, =handle, true",
    "GetStatementStatus_path_statementHandle, /api/v2/statements/01b2c3d4-0000-1111-0000-000000000001, false",
    "CancelStatement_path_statementHandle, '', false"
  })
  void typedParametersRejectInvalidValues(String id, String value, boolean valid) {
    Property parameter = property(id);

    assertThat(parameter.getConstraints().notEmpty()).isEqualTo(id.contains("_path_"));
    assertThat(Pattern.matches(parameter.getConstraints().pattern().value(), value))
        .isEqualTo(valid);
  }

  @Test
  void dropsUnsetQueryParameters() {
    assertThat(template.properties())
        .filteredOn(p -> new ZeebeInput("queryParameters").equals(p.getBinding()))
        .extracting(p -> (String) p.getValue())
        .hasSize(3)
        .allSatisfy(value ->
            assertThat(value).startsWith("=context(get entries({").endsWith("})[value != null])"));
  }

  @Test
  void runStatementBodyPointsToBindings() {
    List<Property> bodies =
        template.properties().stream()
            .filter(p -> "requestBody".equals(p.getGroup()))
            .filter(p -> !(p instanceof HiddenProperty))
            .toList();

    assertThat(bodies)
        .singleElement()
        .satisfies(body -> {
          assertThat(body.getId()).isEqualTo("SubmitStatement_body");
          assertThat(body.getValue()).isNull();
          assertThat(body.getConstraints().notEmpty()).isTrue();
          assertThat(body.getPlaceholder())
              .startsWith("={")
              .contains("bindings", "?", "value: string(");
          assertThat(body.getTooltip())
              .contains("bindings", "never concatenate", "must be strings", "HTTP 202");
        });
  }

  @Test
  void cancelStatementMapsTheBodyToNullSoABodyProcessVariableIsNotSent() {
    assertThat(property("CancelStatement_body"))
        .isInstanceOf(HiddenProperty.class)
        .satisfies(body -> {
          assertThat(body.getValue()).isEqualTo("=null");
          assertThat(body.getBinding()).isEqualTo(new ZeebeInput("body"));
          assertThat(body.getCondition()).isEqualTo(new Equals("operationId", "CancelStatement"));
        });
  }

  @Test
  void readTimeoutOutlastsSnowflakesSynchronousWait() {
    assertThat(property("connectionTimeoutInSeconds").getValue()).isEqualTo(20);
    assertThat((Integer) property("readTimeoutInSeconds").getValue()).isGreaterThan(45);
  }

  @Test
  void doesNotRetryByDefault() {
    assertThat(property("retryCount").getValue()).isEqualTo("0");
    assertThat(property("retryCount").getDescription()).isNull();
  }

  private static Property property(String id) {
    return template.properties().stream()
        .filter(p -> Objects.equals(id, p.getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No property " + id));
  }
}
