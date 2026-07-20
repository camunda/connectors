/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.salesforce;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.camunda.connector.generator.dsl.CommonProperties;
import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.ElementTemplateBuilder;
import io.camunda.connector.generator.dsl.ElementTemplateCategory;
import io.camunda.connector.generator.dsl.ElementTemplateIcon;
import io.camunda.connector.generator.dsl.Engines;
import io.camunda.connector.generator.dsl.GroupStep;
import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.LeafStep;
import io.camunda.connector.generator.dsl.Preset;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeTaskHeader;
import io.camunda.connector.generator.dsl.PropertyCondition.AllMatch;
import io.camunda.connector.generator.dsl.PropertyCondition.Equals;
import io.camunda.connector.generator.dsl.PropertyCondition.OneOf;
import io.camunda.connector.generator.dsl.PropertyConstraints;
import io.camunda.connector.generator.dsl.PropertyGroup;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.dsl.TextProperty;
import io.camunda.connector.generator.java.ClassBasedTemplateGenerator;
import io.camunda.connector.generator.java.annotation.BpmnType;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.json.ElementTemplateModule;
import io.camunda.connector.http.rest.HttpJsonFunction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Generates the Salesforce element template by extending HTTP JSON's own generated {@link
 * ElementTemplate} object model via {@link ElementTemplateBuilder#from(ElementTemplate)}: the
 * inherited authentication block is pruned down to the two mechanisms Salesforce supports (so it
 * stays in sync with HTTP JSON's auth model as it evolves), every other inherited group/property
 * (raw url/method, headers, tls, timeout, retries, output, errors, ...) is dropped, and every
 * Salesforce-specific property (operation type, sObject/SOQL fields, URL construction) is
 * hand-built on top using the same DSL builders HTTP JSON's own generator uses. Salesforce still
 * executes as {@code io.camunda:http-json:1} at runtime -- there is no Salesforce-specific runtime
 * code, only this generated element template.
 *
 * <p>Run manually after model changes and commit the regenerated {@code
 * element-templates/salesforce-connector.json}: {@code mvn -pl connectors/salesforce test-compile
 * exec:java -Dexec.mainClass=io.camunda.connector.salesforce.GenerateElementTemplate
 * -Dexec.classpathScope=test}
 */
public class GenerateElementTemplate {

  private static final Set<String> KEPT_AUTH_PROPERTY_IDS =
      Set.of("authentication.token", "authentication.clientId", "authentication.clientSecret");
  private static final Set<String> KEPT_AUTH_TYPES =
      Set.of("bearer", "oauth-client-credentials-flow");

  public static void main(String[] args) throws Exception {
    ElementTemplate httpJsonTemplate =
        new ClassBasedTemplateGenerator().generate(HttpJsonFunction.class).get(0);

    DropdownProperty originalAuthTypeDropdown =
        (DropdownProperty)
            httpJsonTemplate.properties().stream()
                .filter(GenerateElementTemplate::isAuthTypeDropdown)
                .findFirst()
                .orElseThrow();

    ElementTemplate salesforceTemplate =
        ElementTemplateBuilder.from(httpJsonTemplate)
            // Keep only the "authentication" properties inherited from HTTP JSON; every other
            // property (raw url/method/headers/queryParameters) is Salesforce-specific and
            // rebuilt from scratch below. Groups are dropped entirely and re-declared by
            // buildOperationalGroups() so their tab order matches the previous hand-authored
            // template exactly -- inheriting them would just append "authentication" after
            // whatever new groups are added, out of order.
            .removePropertyGroups(g -> true)
            .removeProperties(p -> !(isAuthTypeDropdown(p) || idIn(p, KEPT_AUTH_PROPERTY_IDS)))
            // Narrow the inherited auth-type dropdown from HTTP JSON's 6 choices down to the 2
            // Salesforce supports.
            .replaceProperty(prunedAuthTypeDropdown(originalAuthTypeDropdown))
            .id("io.camunda.connectors.Salesforce.v1")
            .name("Salesforce Outbound Connector")
            .version(6)
            .category(ElementTemplateCategory.CONNECTORS)
            .documentationRef(
                "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/salesforce/")
            .description("Call the Salesforce APIs from your process")
            .keywords(
                new String[] {
                  "get record",
                  "create record",
                  "update record",
                  "delete record",
                  "SOQL query",
                  "query records",
                  "CRM",
                  "customer relationship",
                  "sObject",
                  "contact",
                  "lead",
                  "opportunity",
                  "account"
                })
            .appliesTo(BpmnType.TASK)
            .elementType(BpmnType.SERVICE_TASK)
            .engines(new Engines("^8.3"))
            .icon(new ElementTemplateIcon(SALESFORCE_ICON))
            .type("io.camunda:http-json:1")
            .propertyGroups(buildOperationalGroups())
            // HTTP JSON's own oauthTokenEndpoint (user-editable) and clientAuthentication
            // (dropdown) were dropped above -- Salesforce fixes both to a single
            // computed/constant value instead of exposing them, to keep this refactor a pure
            // behavioral no-op against the previous hand-authored template. Same for
            // audience/scopes, which have no counterpart in the previous template and so are
            // simply not carried over.
            //
            // Must come after propertyGroups(): oauthTokenEndpoint's value references baseUrl
            // (declared in the "endpoint" group above), and property order in the generated
            // template is significant -- a property can only reference one appearing earlier in
            // the list (see ElementTemplateBuilder#replaceProperty).
            .properties(
                HiddenProperty.builder()
                    .id("authentication.oauthTokenEndpoint")
                    .description("The OAuth token endpoint")
                    .group("authentication")
                    .value("=baseUrl + \"/services/oauth2/token\"")
                    .binding(new ZeebeInput("authentication.oauthTokenEndpoint"))
                    .condition(new Equals("authentication.type", "oauth-client-credentials-flow"))
                    .build(),
                HiddenProperty.builder()
                    .id("authentication.clientAuthentication")
                    .description("Client authentication type")
                    .group("authentication")
                    .value("credentialsBody")
                    .binding(new ZeebeInput("authentication.clientAuthentication"))
                    .condition(new Equals("authentication.type", "oauth-client-credentials-flow"))
                    .build())
            .steps(buildSteps())
            .presets(buildPresets())
            .build();

    ObjectWriter writer =
        new ObjectMapper()
            .registerModule(new ElementTemplateModule())
            .writer(
                new DefaultPrettyPrinter()
                    .withObjectIndenter(new DefaultIndenter().withLinefeed("\n")));

    Path outputPath = Path.of("element-templates", "salesforce-connector.json").toAbsolutePath();
    Files.createDirectories(outputPath.getParent());
    writer.writeValue(outputPath.toFile(), salesforceTemplate);
    System.out.println("Wrote " + outputPath);
  }

  private static boolean idIn(Property p, Set<String> ids) {
    return p.getId() != null && ids.contains(p.getId());
  }

  private static boolean isAuthTypeDropdown(Property p) {
    return "authentication.type".equals(p.getId());
  }

  private static DropdownProperty prunedAuthTypeDropdown(DropdownProperty original) {
    List<DropdownChoice> prunedChoices =
        original.getChoices().stream()
            .filter(choice -> KEPT_AUTH_TYPES.contains(choice.value()))
            .toList();
    return (DropdownProperty)
        DropdownProperty.builder()
            .choices(prunedChoices)
            .id(original.getId())
            .label(original.getLabel())
            .group(original.getGroup())
            .binding(original.getBinding())
            .build();
  }

  /**
   * All property groups in display order. "authentication" carries no properties of its own here --
   * the actual authentication properties are inherited/hand-built directly into the flat properties
   * list (see {@link #main}) and reference this group only via their own {@code group} field,
   * matching how {@link PropertyGroup#properties()} is {@code @JsonIgnore}d anyway.
   */
  private static List<PropertyGroup> buildOperationalGroups() {
    return List.of(
        PropertyGroup.builder()
            .id("operation")
            .label("Operation")
            .properties(
                DropdownProperty.builder()
                    .choices(
                        List.of(
                            new DropdownChoice("sObject records", "sObject"),
                            new DropdownChoice("SOQL Query", "soqlQuery")))
                    .id("salesforceOperationType")
                    .label("Salesforce operation type")
                    .tooltip(
                        "sObject records to create, get, update, or delete a record; SOQL Query to run a Salesforce Object Query Language query.")
                    .group("operation")
                    .binding(new ZeebeInput("salesforceInteractionType"))
                    .build(),
                DropdownProperty.builder()
                    .choices(
                        List.of(
                            new DropdownChoice("Get record", "get"),
                            new DropdownChoice("Create record", "post"),
                            new DropdownChoice("Update record", "patch"),
                            new DropdownChoice("Delete record", "delete")))
                    .id("interactionType")
                    .label("Interaction type")
                    .group("operation")
                    .binding(new ZeebeInput("method"))
                    .condition(new Equals("salesforceOperationType", "sObject"))
                    .build(),
                HiddenProperty.builder()
                    .id("method")
                    .label("Method")
                    .group("operation")
                    .value("get")
                    .binding(new ZeebeInput("method"))
                    .condition(new Equals("salesforceOperationType", "soqlQuery"))
                    .build())
            .build(),
        PropertyGroup.builder().id("authentication").label("Authentication").build(),
        PropertyGroup.builder()
            .id("endpoint")
            .label("Instance")
            .properties(
                StringProperty.builder()
                    .id("baseUrl")
                    .label("Salesforce base URL")
                    .group("endpoint")
                    .feel(FeelMode.optional)
                    .binding(new ZeebeInput("baseUrl"))
                    .constraints(
                        PropertyConstraints.builder()
                            .notEmpty(true)
                            .pattern(
                                new PropertyConstraints.Pattern(
                                    "^(=|(https?://|\\{\\{secrets\\..+\\}\\}).*$)",
                                    "Must be a http(s) URL."))
                            .build())
                    .build(),
                StringProperty.builder()
                    .id("apiVersion")
                    .label("Salesforce API version")
                    .group("endpoint")
                    .feel(FeelMode.optional)
                    .binding(new ZeebeInput("apiVersion"))
                    .value("v58.0")
                    .constraints(PropertyConstraints.builder().notEmpty(true).build())
                    .build())
            .build(),
        PropertyGroup.builder()
            .id("input")
            .label("Operation")
            .properties(
                StringProperty.builder()
                    .id("objectType")
                    .label("Salesforce object")
                    .placeholder("Account")
                    .group("input")
                    .feel(FeelMode.optional)
                    .binding(new ZeebeInput("objectType"))
                    .constraints(PropertyConstraints.builder().notEmpty(true).build())
                    .condition(new Equals("salesforceOperationType", "sObject"))
                    .build(),
                StringProperty.builder()
                    .id("objectId")
                    .label("Salesforce object ID")
                    .group("input")
                    .feel(FeelMode.optional)
                    .binding(new ZeebeInput("objectId"))
                    .constraints(PropertyConstraints.builder().notEmpty(true).build())
                    .condition(
                        new AllMatch(
                            new OneOf("interactionType", List.of("patch", "get", "delete")),
                            new Equals("salesforceOperationType", "sObject")))
                    .build(),
                StringProperty.builder()
                    .id("relationshipFieldName")
                    .label("Relationship field name")
                    .tooltip("Name of the child relation")
                    .group("input")
                    .feel(FeelMode.optional)
                    .binding(new ZeebeInput("relationshipFieldName"))
                    .optional(true)
                    .condition(
                        new AllMatch(
                            new Equals("interactionType", "get"),
                            new Equals("salesforceOperationType", "sObject")))
                    .build(),
                TextProperty.builder()
                    .id("soqlQuery")
                    .label("SOQL query")
                    .tooltip(
                        "Salesforce Object Query Language statement used to retrieve records. See the <a href=\"https://developer.salesforce.com/docs/atlas.en-us.soql_sosl.meta/soql_sosl/sforce_api_calls_soql.htm\" target=\"_blank\">SOQL reference</a>.")
                    .group("input")
                    .feel(FeelMode.optional)
                    .binding(new ZeebeInput("soqlQuery"))
                    .constraints(PropertyConstraints.builder().notEmpty(true).build())
                    .condition(new Equals("salesforceOperationType", "soqlQuery"))
                    .build(),
                HiddenProperty.builder()
                    .id("queryParametersHiddenSoql")
                    .label("Query parameters")
                    .description("Map of query parameters to add to the request URL")
                    .group("input")
                    .binding(new ZeebeInput("queryParameters"))
                    .value("={\n  q: soqlQuery\n}")
                    .condition(new Equals("salesforceOperationType", "soqlQuery"))
                    .build(),
                StringProperty.builder()
                    .id("queryParameters")
                    .label("Query parameters")
                    .tooltip("Map of query parameters to add to the request URL")
                    .group("input")
                    .feel(FeelMode.required)
                    .binding(new ZeebeInput("queryParameters"))
                    .condition(
                        new AllMatch(
                            new Equals("interactionType", "get"),
                            new Equals("salesforceOperationType", "sObject")))
                    .optional(true)
                    .build(),
                HiddenProperty.builder()
                    .id("urlSObject")
                    .label("URL")
                    .group("input")
                    .binding(new ZeebeInput("url"))
                    .value(
                        "=baseUrl + \"/services/data/\" + apiVersion + \"/sobjects/\" + objectType + string(if objectId != null then \"/\" + objectId else \"\") + string(if relationshipFieldName != null then \"/\" + relationshipFieldName else \"\")")
                    .condition(new Equals("salesforceOperationType", "sObject"))
                    .build(),
                HiddenProperty.builder()
                    .id("urlSoql")
                    .label("URL")
                    .group("input")
                    .binding(new ZeebeInput("url"))
                    .value("=baseUrl + \"/services/data/\" + apiVersion + \"/query\"")
                    .condition(new Equals("salesforceOperationType", "soqlQuery"))
                    .build(),
                StringProperty.builder()
                    .id("body")
                    .label("Record fields")
                    .tooltip("Field values for the Salesforce object, provided as a FEEL context.")
                    .group("input")
                    .feel(FeelMode.required)
                    .binding(new ZeebeInput("body"))
                    .condition(new OneOf("interactionType", List.of("patch", "post")))
                    .constraints(PropertyConstraints.builder().notEmpty(true).build())
                    .build())
            .build(),
        PropertyGroup.builder()
            .id("timeout")
            .label("Connect timeout")
            .properties(
                StringProperty.builder()
                    .id("connectionTimeoutInSeconds")
                    .label("Connection timeout")
                    .tooltip(
                        "Timeout in seconds to establish a connection, or 0 for an infinite timeout.")
                    .group("timeout")
                    .value("20")
                    .binding(new ZeebeInput("connectionTimeoutInSeconds"))
                    .optional(true)
                    .feel(FeelMode.optional)
                    .constraints(
                        PropertyConstraints.builder()
                            .notEmpty(false)
                            .pattern(
                                new PropertyConstraints.Pattern(
                                    "^(=|([0-9]+|\\{\\{secrets\\..+\\}\\})$)",
                                    "Must be a timeout in seconds (default value is 20 seconds) or a FEEL expression"))
                            .build())
                    .build())
            .build(),
        PropertyGroup.builder()
            .id("connector")
            .label("Connector")
            .properties(
                CommonProperties.version(6L)
                    .binding(new ZeebeTaskHeader("elementTemplateVersion"))
                    .build(),
                CommonProperties.id("io.camunda.connectors.Salesforce.v1")
                    .binding(new ZeebeTaskHeader("elementTemplateId"))
                    .build())
            .build(),
        PropertyGroup.builder()
            .id("output")
            .label("Response mapping")
            .properties(
                StringProperty.builder()
                    .id("resultVariable")
                    .label("Result variable")
                    .tooltip(
                        "Name of variable to store the response in. <a href=\"https://docs.camunda.io/docs/components/connectors/use-connectors/#result-variable\" target=\"_blank\">result variable documentation</a>")
                    .group("output")
                    .feel(FeelMode.disabled)
                    .binding(new ZeebeTaskHeader("resultVariable"))
                    .condition(new OneOf("interactionType", List.of("get", "post")))
                    .build(),
                TextProperty.builder()
                    .id("resultExpression")
                    .label("Result expression")
                    .tooltip(
                        "Expression to map the response into process variables. <a href=\"https://docs.camunda.io/docs/components/connectors/use-connectors/#result-expression\" target=\"_blank\">result expression documentation</a>")
                    .group("output")
                    .feel(FeelMode.required)
                    .binding(new ZeebeTaskHeader("resultExpression"))
                    .condition(new OneOf("interactionType", List.of("get", "post")))
                    .build())
            .build(),
        PropertyGroup.builder()
            .id("errors")
            .label("Error handling")
            .properties(
                TextProperty.builder()
                    .id("errorExpression")
                    .label("Error expression")
                    .tooltip(
                        "Expression to handle errors. <a href=\"https://docs.camunda.io/docs/components/connectors/use-connectors/#bpmn-errors\" target=\"_blank\">BPMN error handling documentation</a>")
                    .group("errors")
                    .feel(FeelMode.required)
                    .binding(new ZeebeTaskHeader("errorExpression"))
                    .build())
            .build());
  }

  private static List<io.camunda.connector.generator.dsl.Step> buildSteps() {
    return List.of(
        new GroupStep(
            "sObject record",
            "Read, create, update or delete Salesforce sObject records",
            List.of(
                new LeafStep(
                    "Read record",
                    "Retrieve a Salesforce sObject record by its ID",
                    List.of(
                        "get record",
                        "read record",
                        "fetch record",
                        "retrieve sObject",
                        "lookup record"),
                    "sObject_get"),
                new LeafStep(
                    "Create record",
                    "Create a new Salesforce sObject record",
                    List.of(
                        "create record",
                        "new record",
                        "insert record",
                        "add sObject",
                        "post record"),
                    "sObject_post"),
                new LeafStep(
                    "Update record",
                    "Update an existing Salesforce sObject record",
                    List.of(
                        "update record",
                        "modify record",
                        "patch record",
                        "edit sObject",
                        "change record"),
                    "sObject_patch"),
                new LeafStep(
                    "Delete record",
                    "Delete a Salesforce sObject record by its ID",
                    List.of(
                        "delete record",
                        "remove record",
                        "drop record",
                        "erase sObject",
                        "destroy record"),
                    "sObject_delete"))),
        new LeafStep(
            "SOQL query",
            "Run a Salesforce Object Query Language (SOQL) query to fetch records",
            List.of("SOQL query", "query records", "search records", "run query", "select records"),
            "soqlQuery"));
  }

  private static List<Preset> buildPresets() {
    return List.of(
        new Preset(
            "sObject_get",
            java.util.Map.of("salesforceOperationType", "sObject", "interactionType", "get")),
        new Preset(
            "sObject_post",
            java.util.Map.of("salesforceOperationType", "sObject", "interactionType", "post")),
        new Preset(
            "sObject_patch",
            java.util.Map.of("salesforceOperationType", "sObject", "interactionType", "patch")),
        new Preset(
            "sObject_delete",
            java.util.Map.of("salesforceOperationType", "sObject", "interactionType", "delete")),
        new Preset("soqlQuery", java.util.Map.of("salesforceOperationType", "soqlQuery")));
  }

  private static final String SALESFORCE_ICON =
      "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxOCIgaGVpZ2h0PSIxOCIgZmlsbD0icmdiKDAlLDAlLDAlKSIgeG1sbnM6dj0iaHR0cHM6Ly92ZWN0YS5pby9uYW5vIj48cGF0aCBkPSJNNC44MiAzLjA3NEMzLjM4MyAzLjE5MSAyLjE1NiA0LjE0MSAxLjcwNyA1LjVhMi44MSAyLjgxIDAgMCAwLS4xNzIgMS4wNTkgMi40NCAyLjQ0IDAgMCAwIC4xMjUuOTFsLjA1MS4xNzYtLjI4NS4yODFDLjkxOCA4LjQzNC42MzcgOC45NDUuNSA5LjYyMWE0LjAxIDQuMDEgMCAwIDAgLjAxMiAxLjIwMyAzLjEzIDMuMTMgMCAwIDAgLjg5MSAxLjYyNWMuNDYxLjQ2MS45NjEuNzM0IDEuNTgyLjg3MS4xODguMDM5LjY3Mi4wOS43MjcuMDc0LjAxNi0uMDA4LjA5LS4wMTYuMTYtLjAybC4xMzMtLjAxNi4xMzMuMjI3Yy45MDIgMS41MTIgMi43NTggMi4wMjcgNC4yNjYgMS4xODRhMy40OSAzLjQ5IDAgMCAwIDEuMDgyLTEuMDA0bC4xNDgtLjIzLjIzLjA3YTIuMTMgMi4xMyAwIDAgMCAuODgzLjEzMyAyLjg0IDIuODQgMCAwIDAgMS41Mi0uNSAzLjUyIDMuNTIgMCAwIDAgLjc4MS0uNzYyYy4wNzQtLjEwNS4xNDgtLjE4Ny4xNi0uMTg3cy4xMzMuMDEyLjI2Mi4wMjNjLjI2Mi4wMjcuNjA5LjAwNC45NjUtLjA2NmEzLjk5IDMuOTkgMCAwIDAgMi40OC0xLjY4NCAzLjkgMy45IDAgMCAwIC4xMTMtNC4xMjUgMy45MyAzLjkzIDAgMCAwLTIuNTIzLTEuODUyIDMuMzUgMy4zNSAwIDAgMC0xLjg3OS4wODZsLS4yNS4wN2EyLjI2IDIuMjYgMCAwIDEtLjA5NC0uMTI1IDMuODMgMy44MyAwIDAgMC0uNjIxLS42MDljLS40NTctLjM0NC0xLjA0Ny0uNTU1LTEuNjQxLS41ODItLjcxNS0uMDM5LTEuNDguMjU4LTIuMDUxLjc4MWwtLjE2NC4xNTItLjExMy0uMTIxYy0uNTgyLS42NDUtMS40NjUtMS4wOTQtMi4yNTgtMS4xNTItLjM0OC0uMDI3LS40MTQtLjAyNy0uNjEzLS4wMTJ6bS44NzkuNzdjLjc2Mi4xNzYgMS4zNzEuNjEzIDEuODMyIDEuMzEzbC4yMDcuMjk3Yy4wMTIgMCAuMTAyLS4xMTMuMjAzLS4yNTRhNC40MiA0LjQyIDAgMCAxIC4zNC0uNDAyYy44OTgtLjg4MyAyLjM1NS0uODc5IDMuMjUuMDE2LjE2NC4xNjQuMzEzLjM2Ny40NTcuNjI1LjA0My4wODIuMDkuMTQ1LjA5OC4xNDVzLjEwNS0uMDM1LjIwNy0uMDgyYTMuMjYgMy4yNiAwIDAgMSAxLjYyOS0uMjg1YzEuMjY2LjEyMSAyLjI4NS45MDIgMi43MzggMi4wOTRhNC41NCA0LjU0IDAgMCAxIC4xMDkuMzc1Yy4wNTkuMjMuMDU5LjI3LjA2My43MDcgMCAuNTEyLS4wMi42NDgtLjE1MiAxLjA0N2EzLjE2IDMuMTYgMCAwIDEtLjc3IDEuMjM0IDMuMTYgMy4xNiAwIDAgMS0xLjU1NS44NTljLS4zNC4wODItLjg1Mi4wOTQtMS4yNS4wMzFsLS4zMDEtLjAzOWE1LjY1IDUuNjUgMCAwIDAtLjEwOS4yMDdjLS4zMDUuNTk4LS44MjQgMS4wMzUtMS40MzQgMS4yMTEtLjU5LjE3Mi0xLjE2OC4xMDktMS43NS0uMTg0YTEuMTkgMS4xOSAwIDAgMC0uMTk1LS4wODJjLS4wMTEgMC0uMDgyLjExMy0uMTQ4LjI1LS4yNTQuNTYzLS42MjEuOTY1LTEuMTEzIDEuMjM4YTIuNTggMi41OCAwIDAgMS0yLjM3NSAwYy0uNTM1LS4yOTMtLjkyNi0uNzU4LTEuMTY4LTEuMzc1LS4wODYtLjIyMy0uMDYyLS4yMTEtLjQzNy0uMTQ4YTIuODggMi44OCAwIDAgMS0xLjAwNC0uMDI3Yy0xLjU0Ny0uMzg3LTIuMzQ4LTIuMDg2LTEuNjU2LTMuNTA4YTIuNTggMi41OCAwIDAgMSAuOTAyLTEuMDE2bC4yMTEtLjE0MWExLjU3IDEuNTcgMCAwIDAtLjA4Mi0uMjQyYy0uMTg0LS40OTYtLjI0Ni0uOTA2LS4xOTktMS4zNDguMTMzLTEuMzA1IDEuMDc4LTIuMzE2IDIuMzgzLTIuNTQ3YTMuOTQgMy45NCAwIDAgMSAxLjA3LjAzMXptLjExNyAzLjUxOWEzMi44MyAzMi44MyAwIDAgMC0uMDA0IDEuMDdsLjAwNCAxLjAzOS4xMzcuMDA0Yy4wOTguMDA0LjE0NSAwIC4xNTYtLjAyYTM2LjkzIDM2LjkzIDAgMCAwIC4wMDgtMi4wODIuMzkuMzkgMCAwIDAtLjMwMS0uMDEyem00LjMwOS0uMDA0Yy0uMTg3LjA0Ny0uMzMyLjE5NS0uMzk1LjQwMmEuODIuODIgMCAwIDAtLjA0My4xNzZjMCAuMDktLjAyMy4xMDktLjE0MS4xMDloLS4xMTNsLS4wMjMuMTA1YS44My44MyAwIDAgMC0uMDIzLjEyOWMwIC4wMTYuMDUxLjAyMy4xMTcuMDIzLjA3OCAwIC4xMTcuMDA4LjExNy4wMjNzLS4wNTEuMzAxLS4xMDUuNjI5Yy0uMTcyLjkzOC0uMTkxLjk4NC0uNDQ1Ljk4NEg4LjkzbC0uMDI3LjA3OGMtLjA0Ny4xMjUtLjAzOS4xNDUuMDYzLjE3Mi4xOTkuMDU1LjQ2MS0uMDI3LjU3NC0uMTguMTAyLS4xNDUuMTQ1LS4yOTcuMjctMS4wMDhsLjEyNS0uNjkxLjE3Mi0uMDA4Yy4xNjgtLjAxMi4xNjgtLjAxMi4xODQtLjA3NGEuNzguNzggMCAwIDAgLjAxNi0uMTI1bC4wMDQtLjA1OUg5Ljk4bC4wMTYtLjEwMmMuMDItLjE0MS4wODItLjI3Ny4xNDUtLjMwOS4wMjctLjAxNi4xMDUtLjAyNy4xOC0uMDIzbC4xMjkuMDA0LjAzOS0uMTA5YS4zOC4zOCAwIDAgMCAuMDI3LS4xMTdjLS4wNDMtLjA0My0uMjctLjA1OS0uMzkxLS4wMzF6bS02LjgwOS43MTFjLS4zNTkuMDgyLS40OTYuNDMtLjI1NC42NDguMDY2LjA1OS4xNDEuMDk0LjMzNi4xNTYuMzQ0LjEwNS40MTQuMTg0LjI4NS4zMTYtLjAzNS4wMzktLjA3LjA0My0uMjE5LjA0M3MtLjE5NS0uMDA4LS4zMjQtLjA3Yy0uMDgyLS4wMzktLjE1Mi0uMDYyLS4xNi0uMDUxYS45LjkgMCAwIDAtLjA1NS4xMDlsLS4wMzEuMDkuMTMzLjA2M2MuMzc5LjE3Ni44MzYuMTEzLjk2OS0uMTM3LjA1MS0uMTAyLjA1NS0uMjU0LjAwNC0uMzUycy0uMTQ1LS4xNTYtLjQzLS4yNWMtLjE5NS0uMDY2LS4yNjYtLjA5OC0uMjk3LS4xNDUtLjA0My0uMDU1LS4wNDMtLjA1OS0uMDA4LS4xMTMuMDItLjAzMS4wNjMtLjA2Ni4wOTQtLjA3OC4wNzQtLjAzMS4yNzMtLjAwOC40MjYuMDQ3LjA2Ni4wMjMuMTI5LjAzNS4xMzcuMDIzcy4wMzEtLjA1MS4wNTEtLjA5NGwuMDMxLS4wODItLjA2Ni0uMDQzYTEuMDEgMS4wMSAwIDAgMC0uNjIxLS4wODJ6bTEuNDExLS4wMDRjLS4xNTYuMDIzLS4yNzcuMDYzLS4zMi4wOTRzLS4wMzkuMDMxLjAwOC4xMjljLjAzNS4wODYuMDUxLjEwMi4wNzguMDkuMTg0LS4wNzQuNDY5LS4xMDIuNTktLjA1NS4wNzguMDI3LjEyNS4xMDIuMTI1LjE5NXYuMDc0bC0uMjI3LS4wMDhjLS4yODktLjAwOC0uNDI2LjAzMS0uNTU5LjE1Ni0uMTA1LjEwNS0uMTQ4LjIyMy0uMTI5LjM1OS4wNTUuMzQuNDUzLjQ1NyAxLjEwMi4zMTZsLjEwNS0uMDIzLjAxNi0uMzA5Yy4wMzUtLjY3Ni0uMDMxLS44OTUtLjI4NS0uOTg0YTEuMjYgMS4yNiAwIDAgMC0uNTA0LS4wMzV6bS40MTQuNzQybC4wNjYuMDEydi4zOThsLS4wOTguMDE2Yy0uMjUuMDMxLS40My0uMDA0LS40OC0uMDlhLjM4LjM4IDAgMCAxLS4wMjMtLjEyOWMwLS4wOTQuMDYzLS4xNjQuMTY0LS4xOTlhMS4zIDEuMyAwIDAgMSAuMzcxLS4wMDh6bTEuODItLjczNGMtLjM0LjA5OC0uNTA0LjM0NC0uNDg0Ljc0Mi4wMTYuMzE2LjEzNy41MDQuNDAyLjYwNS4yMjcuMDkuODM2LjA0Ny44MzYtLjA1OWEuODIuODIgMCAwIDAtLjA3NC0uMjAzbC0uMTA5LjAzMWExLjA2IDEuMDYgMCAwIDEtLjI3My4wMzVjLS4yMjMuMDA0LS4zNDgtLjA1NS0uNDE0LS4xOTUtLjAzMS0uMDUxLS4wNTEtLjExMy0uMDUxLS4xNDVWOC44NGwuNDg4LS4wMDQuNDg4LS4wMDh2LS4xNmMwLS4yNzMtLjExNy0uNDY5LS4zMjgtLjU2MmEuOTEuOTEgMCAwIDAtLjQ4LS4wMzF6bS4zMjQuMjQyYS4yOS4yOSAwIDAgMSAuMTc2LjIzNGwuMDA4LjA2My0uMzEyLjAwOGMtLjE2OCAwLS4zMiAwLS4zMzYtLjAwNC0uMDMxLS4wMTIuMDE2LS4xNzIuMDc0LS4yMzQuMDg2LS4wOTguMjQ2LS4xMjUuMzkxLS4wNjZ6bTAgMCIvPjxwYXRoIGQ9Ik04LjQzOCA4LjA3Yy0uMjAzLjA0Ny0uMzI0LjE1Ni0uMzU5LjMyLS4wNTUuMjIzLjA3NC4zNjcuNDEuNDczLjM0NC4xMDUuNDA2LjE0OC4zNjMuMjYyLS4wNTUuMTQ1LS4zMzIuMTYtLjU5OC4wMzUtLjA3OC0uMDM1LS4xNDUtLjA1OS0uMTUyLS4wNDdzLS4wMzEuMDU1LS4wNTUuMTA5bC0uMDMxLjA5LjEzMy4wNjNjLjQ4NC4yMjcgMS4wMTIuMDU5IDEuMDEyLS4zMi0uMDA0LS4yMDctLjEwNS0uMzAxLS40NjktLjQxOGExLjI4IDEuMjggMCAwIDEtLjI3My0uMTA5LjE0LjE0IDAgMCAxIC4wMDQtLjE5NWMuMDY2LS4wNTUuMzA5LS4wNTEuNDguMDEyLjA3LjAyMy4xMzMuMDM5LjE0NS4wMjdzLjAyNy0uMDUxLjA0Ny0uMDk0bC4wMzEtLjA4Mi0uMDY2LS4wNDNhMS4wMSAxLjAxIDAgMCAwLS42MjEtLjA4MnptMi40MS0uMDA0Yy0uMjcuMDU5LS40NTMuMjY2LS40OTYuNTYzLS4wNDcuMzQuMDg2LjY0NS4zMzYuNzc3LjExNy4wNTkuMzIuMDg2LjQ3My4wNTUuMjMtLjAzOS4zNzEtLjE0MS40NzMtLjMzMi4wNTEtLjA5OC4wNTUtLjEyNS4wNTUtLjM2M3MtLjAwNC0uMjY2LS4wNTUtLjM2N2MtLjA3LS4xMzMtLjE3Mi0uMjMtLjMwMS0uMjg1LS4xMTMtLjA1MS0uMzUyLS4wNzQtLjQ4NC0uMDQ3em0uMzcxLjI3N2MuMTE3LjA2Ni4xNDguMTU2LjE0OC40MjIgMCAuMTk1LS4wMDQuMjQ2LS4wNDMuMzA5LS4wNy4xMjEtLjE0NS4xNi0uMzAxLjE2cy0uMjI3LS4wMzktLjI5Ny0uMTcyYy0uMDYyLS4xMTctLjA2Mi0uNDczLS4wMDQtLjU5NC4wODYtLjE3Mi4zMTMtLjIyNy40OTYtLjEyNXptMS4yNS0uMjY1YS43NS43NSAwIDAgMC0uMTM3LjA2M2MtLjA3LjA0Ny0uMDc0LjA0My0uMDc0LS4wMiAwLS4wNTUtLjAwNC0uMDU1LS4xNDUtLjA1MWwtLjE0NS4wMDh2MS4zOTVoLjMwMWwuMDA4LS40NjFjLjAxMi0uMzkxLjAyLS40NzMuMDUxLS41MzEuMDU5LS4xMDkuMTUyLS4xNTYuMzA1LS4xNTZoLjEzM2wuMDM1LS4wOWMuMDU1LS4xNDUuMDQ3LS4xNTYtLjA2Mi0uMTc2LS4xMzctLjAxNi0uMTgtLjAxNi0uMjcuMDJ6bTAgMCIvPjxwYXRoIGQ9Ik0xMy4zOTUgOC4wODJjLS4zMDUuMDgyLS40NzMuMjk3LS40OTIuNjQ1LS4wMjcuNTcuNDA2Ljg2MyAxLjA0My43MDcuMTAyLS4wMjMuMTA1LS4wMzkuMDU1LS4xNjQtLjAyNy0uMDY2LS4wNDMtLjA4Ni0uMDctLjA3NGExLjI2IDEuMjYgMCAwIDEtLjQzNy4wMTJjLS4xODQtLjA2Mi0uMjctLjIwMy0uMjctLjQ0MSAwLS4xOC4wNDctLjI5Ny4xNDgtLjM4My4wOTQtLjA3OC4xNDEtLjA4Ni4zODMtLjA3NGwuMjA3LjAxNi4wMzktLjEwMmMuMDQzLS4xMTcuMDM5LS4xMjEtLjE0NS0uMTUyLS4xNzYtLjAzMS0uMzItLjAyNy0uNDYxLjAxMnptMCAwIi8+PHBhdGggZD0iTTE0LjYxNyA4LjA3OGMtLjMzMi4wOTQtLjQ5Mi4zNTUtLjQ2OS43NTguMDE2LjMwOS4xNjQuNTEyLjQ0MS41OTguMjM4LjA3Ljc5Ny4wMjMuNzk3LS4wN2EuODIuODIgMCAwIDAtLjA3NC0uMjAzbC0uMTA1LjAzMWMtLjA1OS4wMi0uMTg3LjAzNS0uMjgxLjAzNS0uMjc3IDAtLjQxNC0uMDk0LS40NDktLjMwNWwtLjAxNi0uMDgyLjQ4OC0uMDA0LjQ4OC0uMDA4di0uMTcyYy0uMDA0LS4yNy0uMTEzLS40NTMtLjMyNC0uNTUxLS4xMjUtLjA1NS0uMzUyLS4wNjYtLjQ5Ni0uMDI3em0uMzQuMjM4YS4yOS4yOSAwIDAgMSAuMTc2LjIzNGwuMDA4LjA2My0uMzEyLjAwOGMtLjE2OCAwLS4zMiAwLS4zMzYtLjAwNHMtLjAyLS4wMjctLjAwNC0uMDgyYy4wNzgtLjIyMy4yNTQtLjMwNS40NjktLjIxOXptMCAwIi8+PC9zdmc+";
}
