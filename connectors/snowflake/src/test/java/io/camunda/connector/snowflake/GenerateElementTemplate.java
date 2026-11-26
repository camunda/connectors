/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.snowflake;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorMode;
import io.camunda.connector.generator.api.RestTemplateGenerator;
import io.camunda.connector.generator.dsl.ConfigurationProperty;
import io.camunda.connector.generator.dsl.ConfigurationTemplate;
import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.ElementTemplateBuilder;
import io.camunda.connector.generator.dsl.ElementTemplateCategory;
import io.camunda.connector.generator.dsl.ElementTemplateIcon;
import io.camunda.connector.generator.dsl.Engines;
import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.LeafStep;
import io.camunda.connector.generator.dsl.NumberProperty;
import io.camunda.connector.generator.dsl.Preset;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.PropertyBinding.ConfigurationTemplateProperty;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.dsl.PropertyCondition.Equals;
import io.camunda.connector.generator.dsl.PropertyCondition.IsEmpty;
import io.camunda.connector.generator.dsl.PropertyConstraints;
import io.camunda.connector.generator.dsl.PropertyGroup;
import io.camunda.connector.generator.dsl.Step;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.json.ElementTemplateModule;
import io.camunda.connector.generator.openapi.OpenApiGenerationSource;
import io.camunda.connector.generator.openapi.OpenApiOutboundTemplateGenerator;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates the Snowflake element template from Snowflake's published SQL API OpenAPI spec.
 *
 * <p>The connector is template-only: it runs as {@code io.camunda:http-json:1} and has no runtime
 * code. The template is built in three steps:
 *
 * <ol>
 *   <li>{@code snowflake-sqlapi.yaml} is a verbatim, pinned copy of the published spec (see {@link
 *       RefreshOpenApiSpec}). {@code snowflake-sqlapi-overlay.json} is an OpenAPI Overlay with
 *       Camunda's changes: the per-account server, the supported security schemes and no token type
 *       header.
 *   <li>The OpenAPI generator (congen's {@code openapi-outbound}) turns the three SQL API
 *       operations into an element template.
 *   <li>This class curates that output, see {@link #curate(ElementTemplate)}.
 * </ol>
 *
 * <p>Run manually after changing the spec, the overlay or this class, and commit the regenerated
 * {@code element-templates/snowflake-connector.json}: {@code mvn -pl connectors/snowflake
 * test-compile exec:java -Dexec.mainClass=io.camunda.connector.snowflake.GenerateElementTemplate
 * -Dexec.classpathScope=test}
 */
public class GenerateElementTemplate {

  static final String RUN_STATEMENT = "SubmitStatement";
  static final String GET_STATEMENT_STATUS = "GetStatementStatus";
  static final String CANCEL_STATEMENT = "CancelStatement";

  /** The curated operations, in the order the operation dropdown lists them. */
  static final List<Operation> OPERATIONS =
      List.of(
          new Operation(
              RUN_STATEMENT,
              "Run SQL statement",
              "Query or change Snowflake data with a SQL statement",
              List.of("SQL", "query", "select", "insert", "update", "merge", "statement")),
          new Operation(
              GET_STATEMENT_STATUS,
              "Get statement status",
              "Get the result of a Snowflake SQL statement, or HTTP 202 while it still runs",
              List.of("statement status", "poll", "async result", "result partition")),
          new Operation(
              CANCEL_STATEMENT,
              "Cancel statement",
              "Cancel a running Snowflake SQL statement",
              List.of("cancel", "stop statement", "timeout")));

  static final Set<String> OPERATION_IDS =
      OPERATIONS.stream().map(Operation::id).collect(Collectors.toCollection(LinkedHashSet::new));

  static final String TEMPLATE_ID = "io.camunda.connectors.Snowflake.v1";
  static final long TEMPLATE_VERSION = 1L;

  static final String CREDENTIAL_ID = "io.camunda.connectors:snowflake-authentication:1";
  static final String CREDENTIAL_CHOOSER_ID = "serviceAuthenticationConfiguration";

  /**
   * Sent on every call. The SQL API requires a User-Agent and returns JSON only. It rejects a POST
   * whose content type is not JSON with HTTP 415, and the REST connector sends a non-JSON content
   * type when there is no body, as for Cancel statement.
   */
  static final String HEADERS =
      "={Accept: \"application/json\", \"Content-Type\": \"application/json\","
          + " \"User-Agent\": \"Camunda\"}";

  static final int READ_TIMEOUT_SECONDS = 60;

  private static final String OPERATION_PROPERTY_ID = "operationId";
  private static final String DOCUMENTATION_URL =
      "https://docs.camunda.io/docs/8.11/components/connectors/out-of-the-box-connectors/snowflake/";
  private static final String API_REFERENCE_URL =
      "https://docs.snowflake.com/en/developer-guide/sql-api/reference";
  private static final String BINDINGS_URL =
      "https://docs.snowflake.com/en/developer-guide/sql-api/submitting-requests#using-bind-variables-in-a-statement";
  private static final String ACCOUNT_URL_PLACEHOLDER =
      "https://myorg-myaccount.snowflakecomputing.com";
  private static final String TOKEN_TOOLTIP =
      "A Snowflake <a href='https://docs.snowflake.com/en/user-guide/programmatic-access-tokens'"
          + " target='_blank'>programmatic access token</a> or an <a"
          + " href='https://docs.snowflake.com/en/user-guide/oauth-ext-overview'"
          + " target='_blank'>External OAuth</a> access token. Snowflake detects the token type.";

  private static final String STATEMENT_BODY_EXAMPLE =
      """
      ={
        statement: "select * from customers where id = ?",
        timeout: 60,
        database: "MY_DB",
        schema: "PUBLIC",
        warehouse: "MY_WH",
        bindings: {
          "1": {type: "TEXT", value: string(customerId)}
        }
      }\
      """;

  private static final String UUID = "[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}";

  /** Labels and tooltips for the parameters, keyed by the generator's property ID suffix. */
  private static final Map<String, Parameter> PARAMETERS =
      Map.of(
          "query_requestId",
          new Parameter(
              "Request ID",
              null,
              null,
              "Optional UUID that identifies this request. Leave empty to let Snowflake assign"
                  + " one. Snowflake answers a request ID it has seen before with the earlier"
                  + " result and does not run the statement again, so never use a fixed value:"
                  + " set a FEEL expression that is unique per process instance.",
              new PropertyConstraints.Pattern("^(=.*|" + UUID + ")?$", "Enter a UUID.")),
          "query_async",
          new Parameter(
              "Run asynchronously",
              "false",
              null,
              "Set to true to return the statement handle at once. By default, Snowflake waits up"
                  + " to 45 seconds for the result and then returns the statement handle with HTTP"
                  + " 202. Get the result with <b>Get statement status</b>.",
              null),
          "query_nullable",
          new Parameter(
              "Return SQL NULL as null",
              "true",
              null,
              "Set to false to return SQL NULL values as the string 'null'.",
              null),
          "path_statementHandle",
          new Parameter(
              "Statement handle",
              null,
              "e4ce975e-f7ff-4b5e-b15e-bf25f59371ae",
              "The <code>statementHandle</code> in the response body of <b>Run SQL"
                  + " statement</b>. While the statement runs, Get statement status returns HTTP"
                  + " 202 with no rows: check <code>response.status</code> and call it again later.",
              new PropertyConstraints.Pattern("^(=.*|" + UUID + ")$", "Enter a statement handle.")),
          "query_partition",
          new Parameter(
              "Partition",
              null,
              "0",
              "Number of the result partition to return, from 0. Large results are split into"
                  + " partitions: <code>resultSetMetaData.partitionInfo</code> lists them.",
              new PropertyConstraints.Pattern("^(=.*|\\d+)?$", "Enter a number from 0.")));

  public static void main(String[] args) throws Exception {
    Path outputPath = templateOutputPath();
    Files.createDirectories(outputPath.getParent());
    ObjectMapper mapper = new ObjectMapper().registerModule(new ElementTemplateModule());
    Files.writeString(outputPath, prettyPrinter(mapper).writeValueAsString(generate()));
    System.out.println("Wrote " + outputPath);
  }

  static ObjectWriter prettyPrinter(ObjectMapper mapper) {
    DefaultIndenter lfIndenter = new DefaultIndenter().withLinefeed("\n");
    return mapper
        .writer()
        .with(
            new DefaultPrettyPrinter()
                .withArrayIndenter(lfIndenter)
                .withObjectIndenter(lfIndenter));
  }

  /**
   * Resolves paths via this class's compiled location, not the working directory: {@code mvn
   * exec:java} runs from wherever {@code mvn} was invoked.
   */
  static Path moduleRoot() throws URISyntaxException {
    return Path.of(
            GenerateElementTemplate.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI())
        // .../target/test-classes -> .../target -> module root
        .getParent()
        .getParent();
  }

  static Path templateOutputPath() throws URISyntaxException {
    return moduleRoot().resolve("element-templates").resolve("snowflake-connector.json");
  }

  static Path specPath() throws URISyntaxException {
    return moduleRoot().resolve("src/test/resources/snowflake-sqlapi.yaml");
  }

  static ElementTemplate generate() throws IOException {
    var source = generationSource();
    var generator = new OpenApiOutboundTemplateGenerator(false);

    List<String> unsupported =
        generator.operations(source).stream()
            .filter(op -> !op.supported())
            .map(RestTemplateGenerator.Operation::id)
            .toList();
    if (!unsupported.isEmpty()) {
      throw new IllegalStateException(
          "The OpenAPI generator no longer supports the curated operations " + unsupported);
    }

    var configuration =
        new GeneratorConfiguration(
            ConnectorMode.NORMAL,
            TEMPLATE_ID,
            "Snowflake Outbound Connector",
            TEMPLATE_VERSION,
            null,
            Map.of());
    return curate(generator.generate(source, configuration).getFirst());
  }

  /**
   * The patched spec with the curated operations, in the OpenAPI generator's CLI form: the spec
   * content first, then the operation IDs.
   */
  static OpenApiGenerationSource generationSource() throws IOException {
    List<String> parameters = new ArrayList<>();
    parameters.add(new ObjectMapper().writeValueAsString(patchedSpec()));
    parameters.addAll(OPERATION_IDS);
    return new OpenApiGenerationSource(parameters);
  }

  /** The pinned spec with the overlay applied. */
  static JsonNode patchedSpec() throws IOException {
    JsonNode spec = readResource(new YAMLMapper(), "/snowflake-sqlapi.yaml");
    JsonNode overlay = readResource(new ObjectMapper(), "/snowflake-sqlapi-overlay.json");
    return OpenApiOverlay.apply(spec, overlay);
  }

  /**
   * Turns the OpenAPI generator's output into the shipped template. Each change fixes something the
   * raw output gets wrong for Snowflake:
   *
   * <ul>
   *   <li>Operation names: the raw dropdown shows {@code POST /api/v2/statements}.
   *   <li>Account URL: the raw server URL is a template ({@code https://{account}...}) that the
   *       generator copies literally. It becomes a required field with no default.
   *   <li>Parameters: the raw output uses spec examples as values (a request ID of {@code string},
   *       a fixed statement handle, partition 2). Values become placeholders, labels and tooltips
   *       are rewritten. Unset query parameters are dropped instead of sent as empty values.
   *   <li>Headers: {@code Accept} and {@code User-Agent} were task fields with example values. They
   *       are fixed and hidden.
   *   <li>Request body: Run SQL statement gets an example that uses {@code bindings}, so process
   *       variables are not concatenated into SQL. Cancel statement has no request body.
   *   <li>Authentication: a reusable Snowflake credential, with the inline token as fallback.
   *   <li>Timeouts: Snowflake holds a synchronous request for up to 45 seconds before it returns
   *       the statement handle, so the read timeout defaults to {@value #READ_TIMEOUT_SECONDS}
   *       seconds instead of the runtime's 20.
   *   <li>Retries default to 0: a retried statement would run again, and could write twice.
   * </ul>
   */
  static ElementTemplate curate(ElementTemplate raw) {
    Map<String, Operation> operationsById =
        OPERATIONS.stream()
            .collect(Collectors.toMap(Operation::id, op -> op, (a, b) -> a, LinkedHashMap::new));

    List<Property> properties = new ArrayList<>();
    for (Property property : raw.properties()) {
      if ("authentication".equals(property.getGroup()) || isDropped(property)) {
        continue;
      }
      properties.add(curateProperty(property, operationsById));
    }

    ElementTemplateBuilder builder =
        ElementTemplateBuilder.from(raw)
            .removeProperties(p -> true)
            .removePropertyGroups(g -> true)
            .removeConfigurationTemplates(ct -> true)
            .configurationTemplates(List.of(credentialType()))
            .propertyGroups(
                List.of(
                    PropertyGroup.builder().id("operation").label("Operation").build(),
                    PropertyGroup.builder().id("server").label("Account").build(),
                    PropertyGroup.builder().id("authentication").label("Authentication").build(),
                    PropertyGroup.builder().id("parameters").label("Parameters").build(),
                    PropertyGroup.builder().id("requestBody").label("Request body").build(),
                    PropertyGroup.builder().id("url").label("URL").build(),
                    PropertyGroup.builder().id("timeout").label("Timeout").build(),
                    PropertyGroup.builder().id("output").label("Output mapping").build(),
                    PropertyGroup.builder().id("error").label("Error handling").build(),
                    PropertyGroup.builder().id("retries").label("Retries").build()))
            .properties(properties)
            .properties(authenticationProperties(raw))
            .properties(timeoutProperties());

    return builder
        .name("Snowflake Outbound Connector")
        .description("Run SQL statements on Snowflake")
        .keywords(
            new String[] {
              "Snowflake", "SQL", "query", "data warehouse", "statement", "database", "table"
            })
        .category(ElementTemplateCategory.CONNECTORS)
        .documentationRef(DOCUMENTATION_URL)
        .engines(new Engines("^8.11"))
        .icon(
            ElementTemplateIcon.from(
                "snowflake-icon.svg", GenerateElementTemplate.class.getClassLoader()))
        .steps(steps())
        .presets(presets())
        .reorderPropertiesByGroup(
            List.of(
                "operation",
                "server",
                "authentication",
                "parameters",
                "requestBody",
                "url",
                "timeout",
                "output",
                "error",
                "retries"))
        .build();
  }

  /**
   * The header fields, which {@link #HEADERS} replaces, and Cancel statement's body: the spec
   * declares none, but the generator emits an empty one.
   */
  private static boolean isDropped(Property property) {
    String id = property.getId();
    return id != null
        && (id.endsWith("_header_Accept")
            || id.endsWith("_header_User-Agent")
            || id.equals(CANCEL_STATEMENT + "_body"));
  }

  private static Property curateProperty(Property property, Map<String, Operation> operations) {
    if (OPERATION_PROPERTY_ID.equals(property.getId())) {
      return ((DropdownProperty) property)
          .toBuilder()
              .choices(
                  operations.values().stream()
                      .map(op -> new DropdownChoice(op.name(), op.id()))
                      .toList())
              .label("Operation")
              .value(OPERATIONS.getFirst().id())
              .build();
    }
    if ("baseUrl".equals(property.getId())) {
      return property.toBuilder()
          .label("Account URL")
          .value(null)
          .placeholder(ACCOUNT_URL_PLACEHOLDER)
          .tooltip(
              "Your Snowflake account URL, <code>https://&lt;orgname&gt;-&lt;account_name&gt;"
                  + ".snowflakecomputing.com</code>, without a path. Find it in Snowsight under"
                  + " <b>Account details</b>.")
          .constraints(
              PropertyConstraints.builder()
                  .notEmpty(true)
                  .pattern(
                      new PropertyConstraints.Pattern(
                          "^(=.*|https://[^/\\s]+)$",
                          "Enter an https:// URL without a path or trailing slash."))
                  .build())
          .build();
    }
    if ("retryCount".equals(property.getId())) {
      return property.toBuilder()
          .value("0")
          .description(null)
          .tooltip("Number of retries. Defaults to 0: a retried statement runs again.")
          .build();
    }
    if ("parameters".equals(property.getGroup())) {
      return curateParameter(property);
    }
    if ("requestBody".equals(property.getGroup())) {
      return curateBody(property, operations.get(operationOf(property)));
    }
    return property;
  }

  private static Property curateParameter(Property property) {
    if (property instanceof HiddenProperty) {
      if (property.getBinding() instanceof ZeebeInput input) {
        if ("headers".equals(input.name())) {
          return property.toBuilder().value(HEADERS).build();
        }
        if ("queryParameters".equals(input.name()) && !"={}".equals(property.getValue())) {
          // An unset optional parameter evaluates to null, and the HTTP client would still send
          // it as an empty query parameter.
          String map = ((String) property.getValue()).substring(1);
          return property.toBuilder()
              .value("=context(get entries(" + map + ")[value != null])")
              .build();
        }
      }
      return property;
    }
    String id = property.getId();
    Parameter parameter =
        PARAMETERS.entrySet().stream()
            .filter(e -> id.endsWith("_" + e.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("No label for the generated parameter " + id));
    var builder =
        property.toBuilder()
            .label(parameter.label())
            .tooltip(parameter.tooltip())
            .description(null)
            .value(parameter.value())
            .placeholder(parameter.placeholder());
    if (property.getOptional() != null && property.getOptional()) {
      // An optional field that is left empty creates no input mapping, so the query parameter
      // expression would read a process variable of the same name, such as a requestId the
      // process uses for its own purposes. A non-optional field always maps its input, to null
      // when it is empty.
      builder.optional(false);
    }
    if (parameter.pattern() != null) {
      boolean required =
          property.getConstraints() != null
              && Boolean.TRUE.equals(property.getConstraints().notEmpty());
      builder.constraints(
          PropertyConstraints.builder().notEmpty(required).pattern(parameter.pattern()).build());
    }
    return builder.build();
  }

  private static Property curateBody(Property property, Operation operation) {
    if (!RUN_STATEMENT.equals(operation.id())) {
      throw new IllegalStateException("Unexpected request body for " + operation.id());
    }
    return property.toBuilder()
        .label("Request body")
        .value(null)
        .placeholder(STATEMENT_BODY_EXAMPLE)
        .tooltip(
            "Request body as a FEEL context. <code>statement</code> is required. Pass process"
                + " variables in <a href='"
                + BINDINGS_URL
                + "' target='_blank'><code>bindings</code></a> and use <code>?</code> in the"
                + " statement, never concatenate them into the SQL. Binding values must be"
                + " strings, also for numbers: use <code>string(...)</code>. Set"
                + " <code>warehouse</code>, <code>database</code>, <code>schema</code> and"
                + " <code>role</code> unless the user's defaults apply. If the statement takes"
                + " longer than 45 seconds, the response is HTTP 202 with a"
                + " <code>statementHandle</code> and no rows: get the result with <b>Get statement"
                + " status</b>. See the <a href='"
                + API_REFERENCE_URL
                + "' target='_blank'>SQL API reference</a>.")
        .constraints(PropertyConstraints.builder().notEmpty(true).build())
        .build();
  }

  private static String operationOf(Property property) {
    if (property.getCondition() instanceof Equals equals
        && OPERATION_PROPERTY_ID.equals(equals.property())) {
      return String.valueOf(equals.equals());
    }
    throw new IllegalStateException(
        "Property " + property.getId() + " is not conditioned on a single operation");
  }

  /**
   * The reusable credential chooser, then the inline token as a fallback. The generator's inline
   * properties (the bearer type as hidden constants, and the token itself) are kept but hidden once
   * a credential is bound.
   */
  private static List<Property> authenticationProperties(ElementTemplate raw) {
    List<Property> result = new ArrayList<>();
    result.add(
        ConfigurationProperty.builder()
            .configurationTemplate(CREDENTIAL_ID)
            .id(CREDENTIAL_CHOOSER_ID)
            .label("Authentication credential")
            .tooltip(
                "Choose a reusable Snowflake credential, or configure the one-time access token"
                    + " below.")
            .optional(true)
            .group("authentication")
            .binding(new ZeebeInput(CREDENTIAL_CHOOSER_ID))
            .build());

    PropertyCondition noCredential = new IsEmpty(CREDENTIAL_CHOOSER_ID, true);
    for (Property property : raw.properties()) {
      if (!"authentication".equals(property.getGroup())) {
        continue;
      }
      if (property.getCondition() != null) {
        throw new IllegalStateException(
            "Unexpected conditional authentication property "
                + property.getId()
                + ": the patched spec should offer bearer authentication only");
      }
      var builder = property.toBuilder().condition(noCredential);
      if ("authentication.token".equals(property.getId())) {
        builder.label("Access token").tooltip(TOKEN_TOOLTIP).value(null).secret(true);
      }
      result.add(builder.build());
    }
    return result;
  }

  /**
   * One credential type for Snowflake only, so the chooser offers only Snowflake credentials. It
   * fixes bearer authentication with a hidden constant and saves into {@code
   * ServiceAuthenticationConfiguration}, like the other single-service http-json connectors.
   */
  static ConfigurationTemplate credentialType() {
    return new ConfigurationTemplate(
        CREDENTIAL_ID,
        "CREDENTIAL",
        1L,
        "Snowflake Authentication",
        List.of(
            HiddenProperty.builder()
                .id("authentication.type")
                .group("authentication")
                .value("bearer")
                .binding(new ConfigurationTemplateProperty("authentication.type"))
                .build(),
            StringProperty.builder()
                .id("authentication.token")
                .label("Access token")
                .tooltip(TOKEN_TOOLTIP)
                .group("authentication")
                .binding(new ConfigurationTemplateProperty("authentication.token"))
                .constraints(PropertyConstraints.builder().notEmpty(true).build())
                .secret(true)
                // Credential values are literals or secret references, never FEEL.
                .feel(FeelMode.disabled)
                .build()));
  }

  /**
   * The connection and read timeouts of the REST connector. The read timeout is longer than the
   * REST connector's 20 seconds: Snowflake answers a synchronous statement after up to 45 seconds.
   */
  private static List<Property> timeoutProperties() {
    return List.of(
        timeoutProperty(
            "connectionTimeoutInSeconds",
            "Connection timeout in seconds",
            "Time to establish the connection. Use 0 for an infinite timeout.",
            20),
        timeoutProperty(
            "readTimeoutInSeconds",
            "Read timeout in seconds",
            "Time to wait for the response. Snowflake waits up to 45 seconds for a result before"
                + " it returns the statement handle, so keep this above 45. Use 0 for an infinite"
                + " timeout.",
            READ_TIMEOUT_SECONDS));
  }

  private static Property timeoutProperty(String id, String label, String tooltip, int value) {
    return NumberProperty.builder()
        .id(id)
        .label(label)
        .tooltip(tooltip)
        .value(value)
        .optional(false)
        .constraints(
            PropertyConstraints.builder()
                .notEmpty(true)
                .pattern(new PropertyConstraints.Pattern("^\\d+$", "Must be a number"))
                .build())
        .feel(FeelMode.staticFeel)
        .group("timeout")
        .binding(new ZeebeInput(id))
        .build();
  }

  private static List<Step> steps() {
    return OPERATIONS.stream()
        .<Step>map(op -> new LeafStep(op.name(), op.description(), op.keywords(), op.id()))
        .toList();
  }

  private static List<Preset> presets() {
    return OPERATIONS.stream()
        .map(op -> new Preset(op.id(), Map.of(OPERATION_PROPERTY_ID, op.id())))
        .toList();
  }

  private static JsonNode readResource(ObjectMapper mapper, String name) throws IOException {
    try (InputStream in = GenerateElementTemplate.class.getResourceAsStream(name)) {
      if (in == null) {
        throw new IllegalStateException("Missing test resource " + name);
      }
      return mapper.readTree(in);
    }
  }

  /**
   * A curated operation.
   *
   * @param id the spec's operationId
   * @param name the dropdown and step name
   * @param description the step description
   * @param keywords step search keywords
   */
  record Operation(String id, String name, String description, List<String> keywords) {}

  /**
   * Curation of a generated parameter.
   *
   * @param label the field label
   * @param value the default value, only where it is Snowflake's own default, or null
   * @param placeholder the placeholder, or null
   * @param tooltip the tooltip
   * @param pattern the value pattern, which must accept an empty value, or null
   */
  record Parameter(
      String label,
      String value,
      String placeholder,
      String tooltip,
      PropertyConstraints.Pattern pattern) {}
}
