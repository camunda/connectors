/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.mixpanel;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
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
import io.camunda.connector.generator.openapi.OpenApiOverlay;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates the Mixpanel element template from Mixpanel's published OpenAPI specs.
 *
 * <p>The connector is template-only: it runs as {@code io.camunda:http-json:1} and has no runtime
 * code. The template is built in three steps:
 *
 * <ol>
 *   <li>{@code mixpanel-openapi.json} is a pinned, trimmed copy of the operations below from
 *       Mixpanel's ingestion and query specs (see {@link RefreshOpenApiSpec}). {@code
 *       mixpanel-openapi-overlay.json} applies Camunda's spec fixes as an OpenAPI Overlay: service
 *       account authentication only, and no Content-Type or Content-Encoding header fields.
 *   <li>The OpenAPI generator (congen's {@code openapi-outbound}) turns the curated operations into
 *       an element template.
 *   <li>{@link #curate(ElementTemplate)} fixes what the raw output gets wrong for Mixpanel.
 * </ol>
 *
 * <p>Run manually after changing the spec or this class, and commit the regenerated {@code
 * element-templates/mixpanel-connector.json}: {@code mvn -pl connectors/mixpanel test-compile
 * exec:java -Dexec.mainClass=io.camunda.connector.mixpanel.GenerateElementTemplate
 * -Dexec.classpathScope=test}
 */
public class GenerateElementTemplate {

  /**
   * The curated operations, in the order the operation dropdown lists them. Up to 20 is a ceiling,
   * not a target: an operation is listed only if processes use it.
   */
  static final List<Operation> OPERATIONS =
      List.of(
          new Operation(
              "import-events",
              "Import event",
              "Record a process milestone as a Mixpanel event",
              List.of("track event", "import event", "send event", "milestone", "analytics"),
              Map.of(
                  "us", "https://api.mixpanel.com",
                  "eu", "https://api-eu.mixpanel.com",
                  "in", "https://api-in.mixpanel.com")),
          new Operation(
              "insights-query",
              "Query saved report",
              "Get the results of a saved Mixpanel Insights report",
              List.of("query report", "insights", "KPI", "metrics", "saved report"),
              Map.of(
                  "us", "https://mixpanel.com/api/query",
                  "eu", "https://eu.mixpanel.com/api/query",
                  "in", "https://in.mixpanel.com/api/query")));

  static final Set<String> OPERATION_IDS =
      OPERATIONS.stream().map(Operation::id).collect(Collectors.toCollection(LinkedHashSet::new));

  static final String TEMPLATE_ID = "io.camunda.connectors.Mixpanel.v1";
  static final long TEMPLATE_VERSION = 1L;

  static final String CREDENTIAL_ID = "io.camunda.connectors:mixpanel-authentication:1";
  static final String CREDENTIAL_CHOOSER_ID = "serviceAuthenticationConfiguration";

  /**
   * The event time in epoch milliseconds, taken once when the task starts. Input mappings are not
   * evaluated again when the job is retried, so a retry sends the same time and the same {@code
   * $insert_id}, and Mixpanel drops the duplicate.
   */
  static final String EVENT_TIME =
      "=(now() - date and time(\"1970-01-01T00:00:00Z\")) / duration(\"PT0.001S\")";

  private static final String OPERATION_PROPERTY_ID = "operationId";
  private static final String REGION_PROPERTY_ID = "region";
  private static final String DOCUMENTATION_URL =
      "https://docs.camunda.io/docs/8.11/components/connectors/out-of-the-box-connectors/mixpanel/";
  private static final String USERNAME_TOOLTIP =
      "Username of a Mixpanel service account with access to the project. Create one in Mixpanel"
          + " under Organization settings or Project settings > Service accounts.";
  private static final String SECRET_TOOLTIP = "Secret of the Mixpanel service account.";

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
    return moduleRoot().resolve("element-templates").resolve("mixpanel-connector.json");
  }

  static Path specPath() throws URISyntaxException {
    return moduleRoot().resolve("src/test/resources/mixpanel-openapi.json");
  }

  static Path overlayPath() throws URISyntaxException {
    return moduleRoot().resolve("src/test/resources/mixpanel-openapi-overlay.json");
  }

  static ElementTemplate generate() throws IOException, URISyntaxException {
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
            "Mixpanel Outbound Connector",
            TEMPLATE_VERSION,
            null,
            Map.of());
    return curate(generator.generate(source, configuration).getFirst());
  }

  /**
   * The pinned spec with the curated operations, in the OpenAPI generator's CLI form: the spec
   * content first, then the operation IDs. External references are never resolved.
   */
  static OpenApiGenerationSource generationSource() throws IOException, URISyntaxException {
    List<String> parameters = new ArrayList<>();
    parameters.add(new ObjectMapper().writeValueAsString(patchedSpec()));
    parameters.addAll(OPERATION_IDS);
    parameters.add("--no-resolve-refs");
    return new OpenApiGenerationSource(parameters);
  }

  /** The pinned spec with the overlay applied. */
  static JsonNode patchedSpec() throws IOException, URISyntaxException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode spec = mapper.readTree(Files.readString(specPath()));
    JsonNode overlay = mapper.readTree(Files.readString(overlayPath()));
    return OpenApiOverlay.apply(spec, overlay);
  }

  /**
   * Turns the OpenAPI generator's output into the shipped template. Each change fixes something the
   * raw output gets wrong for Mixpanel and that an OpenAPI Overlay cannot fix:
   *
   * <ul>
   *   <li>Operation names: the raw dropdown shows {@code POST /import}.
   *   <li>Server: the two operations live on different hosts, and the specs give each host as a
   *       {@code {region}} variable that the generator does not expand. The generator only reads
   *       the spec's top-level servers, so an overlay cannot give each operation its own host. A
   *       data residency dropdown picks the host for each operation.
   *   <li>Authentication: a reusable Mixpanel service account credential, with the service account
   *       entered on the task as the fallback. The raw output offers a separate authentication
   *       dropdown per operation.
   *   <li>Project ID: every call needs it as a query parameter. One field serves both operations.
   *       It stays on the task: a credential can only add authentication, not a query parameter.
   *   <li>Import event: the raw body is an empty {@code =}. It gets named fields, and the body adds
   *       the time and {@code $insert_id} that Mixpanel requires, so a retried task is not counted
   *       twice. {@code strict=1} makes Mixpanel reject invalid events with HTTP 400, which fails
   *       the job.
   *   <li>Query saved report: the raw query parameters default to 0. They become a report ID and an
   *       optional workspace ID with help text.
   * </ul>
   */
  static ElementTemplate curate(ElementTemplate raw) {
    List<Property> generated = new ArrayList<>();
    for (Property property : raw.properties()) {
      Property curated = curateProperty(property);
      if (curated != null) {
        generated.add(curated);
      }
    }

    return ElementTemplateBuilder.from(raw)
        .removeProperties(p -> true)
        .removePropertyGroups(g -> true)
        .removeConfigurationTemplates(ct -> true)
        .configurationTemplates(List.of(credentialType()))
        .propertyGroups(
            List.of(
                PropertyGroup.builder().id("operation").label("Operation").build(),
                PropertyGroup.builder().id("authentication").label("Authentication").build(),
                PropertyGroup.builder().id("project").label("Project").build(),
                PropertyGroup.builder().id("parameters").label("Parameters").build(),
                PropertyGroup.builder().id("url").label("URL").build(),
                PropertyGroup.builder().id("output").label("Output mapping").build(),
                PropertyGroup.builder().id("error").label("Error handling").build(),
                PropertyGroup.builder().id("retries").label("Retries").build()))
        // Zeebe applies input mappings in order. The hidden base URLs, query parameters and body
        // read the fields below, so those come first within their groups.
        .properties(authenticationProperties())
        .properties(projectProperties())
        .properties(inputProperties())
        .properties(generated)
        .name("Mixpanel Outbound Connector")
        .description("Record process milestones as Mixpanel events and query saved reports")
        .keywords(
            new String[] {
              "Mixpanel", "product analytics", "import event", "track event", "insights", "report"
            })
        .category(ElementTemplateCategory.CONNECTORS)
        .documentationRef(DOCUMENTATION_URL)
        .engines(new Engines("^8.11"))
        .icon(
            ElementTemplateIcon.from(
                "mixpanel-icon.svg", GenerateElementTemplate.class.getClassLoader()))
        .steps(steps())
        .presets(presets())
        .reorderPropertiesByGroup(
            List.of(
                "operation",
                "authentication",
                "project",
                "parameters",
                "url",
                "output",
                "error",
                "retries"))
        .build();
  }

  /** Returns the curated property, or null to drop it. */
  private static Property curateProperty(Property property) {
    String id = property.getId();
    if (OPERATION_PROPERTY_ID.equals(id)) {
      return ((DropdownProperty) property)
          .toBuilder()
              .choices(
                  OPERATIONS.stream().map(op -> new DropdownChoice(op.name(), op.id())).toList())
              .label("Operation")
              .value(OPERATIONS.getFirst().id())
              .build();
    }
    if ("baseUrl".equals(id) || "authentication".equals(property.getGroup())) {
      return null;
    }
    if (id == null || !id.contains("_")) {
      return property;
    }
    String operation = operationOf(property);
    if (id.startsWith(operation + "_query_")) {
      return null;
    }
    if (id.equals(operation + "_$headers")) {
      return property.toBuilder().value("={}").build();
    }
    if (id.equals(operation + "_$queryParameters")) {
      return property.toBuilder().value(queryParameters(operation)).build();
    }
    if (id.equals(operation + "_body")) {
      return HiddenProperty.builder()
          .id(id)
          .group("parameters")
          .value(
              "=[{\n  event: eventName,\n  properties: put all(\n    {time: eventTime,"
                  + " \"$insert_id\": string(eventTime), distinct_id: distinctId},\n"
                  + "    if eventProperties = null then {} else eventProperties\n  )\n}]")
          .binding(new ZeebeInput("body"))
          .condition(property.getCondition())
          .build();
    }
    return property;
  }

  private static String queryParameters(String operation) {
    return switch (operation) {
      case "import-events" -> "={project_id: projectId, strict: \"1\"}";
      case "insights-query" ->
          "=context(get entries({project_id: projectId, bookmark_id: reportId,"
              + " workspace_id: workspaceId})[value != null])";
      default -> throw new IllegalStateException("No query parameters for " + operation);
    };
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
   * The reusable credential chooser, then the service account entered on the task as a fallback,
   * hidden once a credential is chosen.
   */
  private static List<Property> authenticationProperties() {
    PropertyCondition noCredential = new IsEmpty(CREDENTIAL_CHOOSER_ID, true);
    return List.of(
        ConfigurationProperty.builder()
            .configurationTemplate(CREDENTIAL_ID)
            .id(CREDENTIAL_CHOOSER_ID)
            .label("Authentication credential")
            .tooltip(
                "Choose a reusable Mixpanel credential, or configure the one-time service account"
                    + " below.")
            .optional(true)
            .group("authentication")
            .binding(new ZeebeInput(CREDENTIAL_CHOOSER_ID))
            .build(),
        HiddenProperty.builder()
            .id("authentication.type")
            .group("authentication")
            .value("basic")
            .binding(new ZeebeInput("authentication.type"))
            .condition(noCredential)
            .build(),
        StringProperty.builder()
            .id("authentication.username")
            .label("Service account username")
            .tooltip(USERNAME_TOOLTIP)
            .group("authentication")
            .binding(new ZeebeInput("authentication.username"))
            .feel(FeelMode.optional)
            .constraints(required())
            .condition(noCredential)
            .build(),
        StringProperty.builder()
            .id("authentication.password")
            .label("Service account secret")
            .tooltip(SECRET_TOOLTIP)
            .group("authentication")
            .binding(new ZeebeInput("authentication.password"))
            .feel(FeelMode.optional)
            .constraints(required())
            .secret(true)
            .condition(noCredential)
            .build());
  }

  /**
   * One credential type for Mixpanel only, so the chooser offers only Mixpanel credentials. It
   * fixes Basic authentication with a hidden constant and saves into {@code
   * ServiceAuthenticationConfiguration}, like the other single-service http-json connectors.
   */
  static ConfigurationTemplate credentialType() {
    return new ConfigurationTemplate(
        CREDENTIAL_ID,
        "CREDENTIAL",
        1L,
        "Mixpanel Authentication",
        List.of(
            HiddenProperty.builder()
                .id("authentication.type")
                .group("authentication")
                .value("basic")
                .binding(new ConfigurationTemplateProperty("authentication.type"))
                .build(),
            credentialField("authentication.username", "Service account username", USERNAME_TOOLTIP)
                .build(),
            credentialField("authentication.password", "Service account secret", SECRET_TOOLTIP)
                .secret(true)
                .build()));
  }

  private static StringProperty.StringPropertyBuilder credentialField(
      String name, String label, String tooltip) {
    var builder = StringProperty.builder();
    builder
        .id(name)
        .label(label)
        .tooltip(tooltip)
        .group("authentication")
        .binding(new ConfigurationTemplateProperty(name))
        .constraints(required())
        // Credential values are literals or secret references, never FEEL.
        .feel(FeelMode.disabled);
    return builder;
  }

  /**
   * The project ID and data residency serve every operation. The data residency picks each
   * operation's host, because the event API and the query API live on different hosts.
   */
  private static List<Property> projectProperties() {
    List<Property> properties = new ArrayList<>();
    properties.add(
        StringProperty.builder()
            .id("projectId")
            .label("Project ID")
            .tooltip(
                "ID of the Mixpanel project. It is the number after /project/ in the Mixpanel URL.")
            .group("project")
            .binding(new ZeebeInput("projectId"))
            .feel(FeelMode.optional)
            .constraints(required())
            .build());
    properties.add(
        DropdownProperty.builder()
            .choices(
                List.of(
                    new DropdownChoice("US (default)", "us"),
                    new DropdownChoice("EU", "eu"),
                    new DropdownChoice("India", "in")))
            .id(REGION_PROPERTY_ID)
            .label("Data residency")
            .tooltip(
                "Where the project's data is stored. Use EU or India only if the project is enrolled"
                    + " in that data residency.")
            .value("us")
            .group("project")
            .binding(new ZeebeInput(REGION_PROPERTY_ID))
            .build());
    for (Operation operation : OPERATIONS) {
      Map<String, String> hosts = operation.hosts();
      properties.add(
          HiddenProperty.builder()
              .id(operation.id() + "_baseUrl")
              .group("project")
              .value(
                  "=if region = \"eu\" then \""
                      + hosts.get("eu")
                      + "\" else if region = \"in\" then \""
                      + hosts.get("in")
                      + "\" else \""
                      + hosts.get("us")
                      + "\"")
              .binding(new ZeebeInput("baseUrl"))
              .condition(new Equals(OPERATION_PROPERTY_ID, operation.id()))
              .build());
    }
    return properties;
  }

  private static List<Property> inputProperties() {
    return List.of(
        field("import-events", "eventName", "Event name", "Name of the event in Mixpanel.")
            .placeholder("Order shipped")
            .constraints(required())
            .build(),
        field(
                "import-events",
                "distinctId",
                "Distinct ID",
                "ID of the user who did the event, as used in the rest of your Mixpanel data.")
            .constraints(required())
            .build(),
        field(
                "import-events",
                "eventProperties",
                "Event properties",
                "Extra event properties as a FEEL context. The time and $insert_id are set"
                    + " automatically; set them here to override.")
            .feel(FeelMode.required)
            .placeholder("={orderId: orderId, amount: total}")
            .optional(true)
            .build(),
        HiddenProperty.builder()
            .id("import-events_eventTime")
            .group("parameters")
            .value(EVENT_TIME)
            .binding(new ZeebeInput("eventTime"))
            .condition(new Equals(OPERATION_PROPERTY_ID, "import-events"))
            .build(),
        field(
                "insights-query",
                "reportId",
                "Report ID",
                "ID of the saved Insights report. In the report's URL it follows report- in the"
                    + " editor-card-id. Mixpanel allows 60 queries per hour.")
            .constraints(required())
            .build(),
        field(
                "insights-query",
                "workspaceId",
                "Workspace ID",
                "ID of the workspace, if the project uses workspaces. It is the number after /view/"
                    + " in the Mixpanel URL.")
            .optional(true)
            .build());
  }

  private static StringProperty.StringPropertyBuilder field(
      String operation, String name, String label, String tooltip) {
    var builder = StringProperty.builder();
    builder
        .id(operation + "_" + name)
        .label(label)
        .tooltip(tooltip)
        .group("parameters")
        .binding(new ZeebeInput(name))
        .feel(FeelMode.optional)
        .condition(new Equals(OPERATION_PROPERTY_ID, operation));
    return builder;
  }

  private static PropertyConstraints required() {
    return PropertyConstraints.builder().notEmpty(true).build();
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

  /**
   * A curated operation.
   *
   * @param id the spec's operationId
   * @param name the dropdown and step name
   * @param description the step description
   * @param keywords step search keywords
   * @param hosts the base URL per data residency region ({@code us}, {@code eu}, {@code in})
   */
  record Operation(
      String id,
      String name,
      String description,
      List<String> keywords,
      Map<String, String> hosts) {}
}
