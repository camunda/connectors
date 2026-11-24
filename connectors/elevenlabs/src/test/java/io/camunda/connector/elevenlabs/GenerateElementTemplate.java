/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.elevenlabs;

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
import io.camunda.connector.generator.dsl.PropertyCondition.AllMatch;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates the ElevenLabs element template from ElevenLabs' published OpenAPI spec.
 *
 * <p>The connector is template-only: it runs as {@code io.camunda:http-json:1} and has no runtime
 * code. The template is built in three steps:
 *
 * <ol>
 *   <li>{@code elevenlabs-openapi.json} is a pinned, trimmed copy of the published spec (see {@link
 *       RefreshOpenApiSpec}). {@code elevenlabs-openapi-overlay.json} adds what the spec lacks
 *       (servers and the {@code xi-api-key} security scheme) as an OpenAPI Overlay.
 *   <li>The OpenAPI generator (congen's {@code openapi-outbound}) turns the curated operations into
 *       an element template.
 *   <li>This class curates that output: operation names, labels and help text, a reusable
 *       ElevenLabs credential, the response format for audio, and the defaults listed in {@link
 *       #curate(ElementTemplate)}.
 * </ol>
 *
 * <p>Run manually after changing the spec, the overlay or this class, and commit the regenerated
 * {@code element-templates/elevenlabs-connector.json}: {@code mvn -pl connectors/elevenlabs
 * test-compile exec:java -Dexec.mainClass=io.camunda.connector.elevenlabs.GenerateElementTemplate
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
              "text_to_speech_full",
              "Text to speech",
              "Generate an ElevenLabs audio file from text and store it as a document",
              List.of("text to speech", "TTS", "generate audio", "voice", "speech synthesis"),
              "={\n  text: \"Your order has shipped.\",\n  model_id: \"eleven_multilingual_v2\"\n}",
              "text",
              "text-to-speech/convert"),
          new Operation(
              "get_user_voices_v2",
              "List voices",
              "List the ElevenLabs voices available to your account, to find a voice ID",
              List.of("list voices", "voice ID", "search voices", "find voice"),
              null,
              null,
              "voices/search"),
          new Operation(
              "create_batch_call",
              "Submit batch call",
              "Let an ElevenLabs agent call a list of phone numbers",
              List.of("batch call", "call list", "bulk calls", "outbound campaign", "voice agent"),
              "={\n  call_name: \"Renewal reminders\",\n  agent_id: \"your-agent-id\","
                  + "\n  agent_phone_number_id: \"your-phone-number-id\","
                  + "\n  recipients: [{phone_number: \"+15551234567\"}]\n}",
              "call_name, agent_id, recipients",
              "batch-calling/create"),
          new Operation(
              "get_batch_call",
              "Get batch call",
              "Get the progress and per-recipient status of an ElevenLabs batch call",
              List.of("batch call status", "call progress", "get batch call"),
              null,
              null,
              "batch-calling/get"),
          new Operation(
              "get_conversation_history_route",
              "Get conversation",
              "Get the transcript and outcome of an ElevenLabs agent conversation",
              List.of("conversation", "call transcript", "call outcome", "get conversation"),
              null,
              null,
              "conversations/get"),
          new Operation(
              "speech_to_text",
              "Transcribe audio",
              "Transcribe an audio or video document to text with ElevenLabs",
              List.of("speech to text", "STT", "transcribe", "transcription", "audio to text"),
              "={\n  model_id: \"scribe_v1\",\n  file: audioDocument\n}",
              "model_id, and file (a document) or cloud_storage_url",
              "speech-to-text/convert"),
          new Operation(
              "get_transcript_by_id",
              "Get transcript",
              "Get the result of an asynchronous ElevenLabs transcription",
              List.of("get transcript", "transcription result", "async transcription"),
              null,
              null,
              "speech-to-text/get"));

  static final Set<String> OPERATION_IDS =
      OPERATIONS.stream().map(Operation::id).collect(Collectors.toCollection(LinkedHashSet::new));

  static final String TEMPLATE_ID = "io.camunda.connectors.ElevenLabs.v1";
  static final long TEMPLATE_VERSION = 1L;

  static final String CREDENTIAL_ID = "io.camunda.connectors:elevenlabs-authentication:1";
  static final String CREDENTIAL_CHOOSER_ID = "serviceAuthenticationConfiguration";

  private static final String OPERATION_PROPERTY_ID = "operationId";
  private static final String DOCUMENTATION_URL =
      "https://docs.camunda.io/docs/8.10/components/connectors/out-of-the-box-connectors/elevenlabs/";
  private static final String API_REFERENCE_URL = "https://elevenlabs.io/docs/api-reference/";
  private static final String API_KEY_TOOLTIP =
      "ElevenLabs API key, sent in the xi-api-key header. Create one in the <a"
          + " href='https://elevenlabs.io/app/settings/api-keys' target='_blank'>ElevenLabs"
          + " dashboard</a>.";

  /** Placeholders for parameters whose spec example is a useful hint. */
  private static final Map<String, String> PARAMETER_PLACEHOLDERS =
      Map.of(
          "text_to_speech_full_path_voice_id", "21m00Tcm4TlvDq8ikWAM",
          "text_to_speech_full_query_output_format", "mp3_44100_128");

  /** Acronyms that a humanized parameter label keeps in upper case. */
  private static final Map<String, String> LABEL_WORDS =
      Map.of("id", "ID", "ids", "IDs", "url", "URL");

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
    return moduleRoot().resolve("element-templates").resolve("elevenlabs-connector.json");
  }

  static Path specPath() throws URISyntaxException {
    return moduleRoot().resolve("src/test/resources/elevenlabs-openapi.json");
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
            "ElevenLabs Outbound Connector",
            TEMPLATE_VERSION,
            null,
            Map.of());
    return curate(
        generator.generate(source, configuration).getFirst(),
        arrayQueryParameterIds(patchedSpec()));
  }

  /**
   * The patched spec with the curated operations, in the OpenAPI generator's CLI form: the spec
   * content first, then the operation IDs. External references are never resolved.
   */
  static OpenApiGenerationSource generationSource() throws IOException {
    List<String> parameters = new ArrayList<>();
    parameters.add(new ObjectMapper().writeValueAsString(patchedSpec()));
    parameters.addAll(OPERATION_IDS);
    parameters.add("--no-resolve-refs");
    return new OpenApiGenerationSource(parameters);
  }

  /** The pinned spec with the overlay applied. */
  static JsonNode patchedSpec() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode spec = readResource(mapper, "/elevenlabs-openapi.json");
    JsonNode overlay = readResource(mapper, "/elevenlabs-openapi-overlay.json");
    return OpenApiOverlay.apply(spec, overlay);
  }

  /**
   * Property IDs of the curated operations' array-valued query parameters, in the OpenAPI
   * generator's {@code <operationId>_query_<name>} form.
   */
  static Set<String> arrayQueryParameterIds(JsonNode spec) {
    Set<String> ids = new LinkedHashSet<>();
    for (JsonNode path : spec.path("paths")) {
      for (JsonNode operation : path) {
        String operationId = operation.path("operationId").asText(null);
        if (operationId == null || !OPERATION_IDS.contains(operationId)) {
          continue;
        }
        for (JsonNode parameter : operation.path("parameters")) {
          if (parameter.has("$ref")) {
            parameter = spec.at(parameter.get("$ref").asText().substring(1));
          }
          if ("query".equals(parameter.path("in").asText()) && isArray(parameter.path("schema"))) {
            ids.add(operationId + "_query_" + parameter.path("name").asText());
          }
        }
      }
    }
    return ids;
  }

  private static boolean isArray(JsonNode schema) {
    if ("array".equals(schema.path("type").asText())) {
      return true;
    }
    for (JsonNode option : schema.path("anyOf")) {
      if ("array".equals(option.path("type").asText())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Turns the OpenAPI generator's output into the shipped template. Each change fixes something the
   * raw output gets wrong for ElevenLabs:
   *
   * <ul>
   *   <li>Operation names: the raw dropdown shows {@code POST /v1/...}.
   *   <li>Parameters: the raw output uses spec examples as default values, so "List voices" would
   *       filter for young male voices by default. Values become placeholders where useful, labels
   *       read as words ({@code Voice ID}, not {@code Voice_id}), and help text moves to tooltips.
   *   <li>Query parameters: an unset optional parameter would be sent as an empty {@code ?gender}.
   *       Null entries are dropped from the query map.
   *   <li>Array query parameters: http-json sends query parameters as a string map, so it cannot
   *       send {@code language=en&language=es}. ElevenLabs reads a comma-separated value as one
   *       value and silently returns nothing, and a FEEL list does not bind. These fields take one
   *       value, enforced by a pattern that rejects commas.
   *   <li>Request bodies get a label, an example and a link to the API reference.
   *   <li>Authentication: a reusable ElevenLabs credential, with the inline API key as fallback.
   *   <li>Text to speech returns audio, so it defaults to "Document reference".
   *   <li>Retries default to 0: a retry would place a second phone call or bill a second
   *       generation.
   * </ul>
   */
  static ElementTemplate curate(ElementTemplate raw, Set<String> arrayQueryParameterIds) {
    Map<String, Operation> operationsById =
        OPERATIONS.stream()
            .collect(Collectors.toMap(Operation::id, op -> op, (a, b) -> a, LinkedHashMap::new));

    List<Property> properties = new ArrayList<>();
    for (Property property : raw.properties()) {
      if ("authentication".equals(property.getGroup())) {
        continue;
      }
      properties.add(curateProperty(property, operationsById, arrayQueryParameterIds));
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
                    PropertyGroup.builder().id("server").label("Server").build(),
                    PropertyGroup.builder().id("authentication").label("Authentication").build(),
                    PropertyGroup.builder().id("parameters").label("Parameters").build(),
                    PropertyGroup.builder().id("requestBody").label("Request body").build(),
                    PropertyGroup.builder().id("url").label("URL").build(),
                    PropertyGroup.builder().id("output").label("Output mapping").build(),
                    PropertyGroup.builder().id("error").label("Error handling").build(),
                    PropertyGroup.builder().id("retries").label("Retries").build()))
            .properties(properties)
            .properties(authenticationProperties(raw))
            .properties(responseFormatProperties());

    return builder
        .name("ElevenLabs Outbound Connector")
        .description("Generate speech, transcribe audio and run AI voice calls with ElevenLabs")
        .keywords(
            new String[] {
              "ElevenLabs",
              "text to speech",
              "speech to text",
              "transcription",
              "voice",
              "AI voice agent",
              "outbound call",
              "batch call"
            })
        .category(ElementTemplateCategory.CONNECTORS)
        .documentationRef(DOCUMENTATION_URL)
        .engines(new Engines("^8.11"))
        .icon(
            ElementTemplateIcon.from(
                "elevenlabs-icon.svg", GenerateElementTemplate.class.getClassLoader()))
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
                "output",
                "error",
                "retries"))
        .build();
  }

  private static Property curateProperty(
      Property property, Map<String, Operation> operations, Set<String> arrayQueryParameterIds) {
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
          .tooltip(
              "ElevenLabs API endpoint. The data residency endpoints need an ElevenLabs"
                  + " enterprise plan with data residency.")
          .build();
    }
    if ("retryCount".equals(property.getId())) {
      return property.toBuilder()
          .value("0")
          .tooltip(
              "Number of retries. Defaults to 0: a retried call or generation is placed and billed"
                  + " again.")
          .build();
    }
    if ("parameters".equals(property.getGroup())) {
      return curateParameter(property, arrayQueryParameterIds.contains(property.getId()));
    }
    if ("requestBody".equals(property.getGroup())) {
      return curateBody(property, operations.get(operationOf(property)));
    }
    return property;
  }

  private static Property curateParameter(Property property, boolean arrayValued) {
    if (property instanceof HiddenProperty) {
      if (property.getBinding() instanceof ZeebeInput input
          && "queryParameters".equals(input.name())
          && !"={}".equals(property.getValue())) {
        // An unset optional parameter evaluates to null, and the HTTP client would still send it
        // as an empty query parameter.
        String map = ((String) property.getValue()).substring(1);
        return property.toBuilder()
            .value("=context(get entries(" + map + ")[value != null])")
            .build();
      }
      return property;
    }
    var builder = property.toBuilder();
    builder.label(humanize(property.getLabel()));
    if (property.getDescription() != null) {
      builder.tooltip(property.getDescription());
      builder.description(null);
    }
    builder.value(null);
    builder.placeholder(PARAMETER_PLACEHOLDERS.get(property.getId()));
    if (arrayValued) {
      String tooltip = property.getDescription() == null ? "" : property.getDescription() + " ";
      builder.tooltip(
          tooltip
              + "One value only. To filter by several values, run the operation once per value.");
      builder.constraints(
          PropertyConstraints.builder()
              .notEmpty(false)
              .pattern(new PropertyConstraints.Pattern("^[^,]*$", "Enter a single value."))
              .build());
    }
    return builder.build();
  }

  private static Property curateBody(Property property, Operation operation) {
    return property.toBuilder()
        .label("Request body")
        .value(null)
        .placeholder(operation.bodyExample())
        .tooltip(
            "Request body as a FEEL context. Required fields: "
                + operation.requiredBodyFields()
                + ". See the <a href='"
                + API_REFERENCE_URL
                + operation.apiReference()
                + "' target='_blank'>ElevenLabs API reference</a>.")
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
   * {@code voice_id} becomes {@code Voice ID}: the OpenAPI generator labels parameters with their
   * raw names.
   */
  static String humanize(String label) {
    String[] words = label.replace('_', ' ').trim().toLowerCase(Locale.ROOT).split("\\s+");
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < words.length; i++) {
      String word = LABEL_WORDS.getOrDefault(words[i], words[i]);
      if (i == 0 && word.equals(words[i])) {
        word = Character.toUpperCase(word.charAt(0)) + word.substring(1);
      }
      result.append(i == 0 ? "" : " ").append(word);
    }
    return result.toString();
  }

  /**
   * The reusable credential chooser, then the inline API key as a fallback. The generator's inline
   * properties (type, header location and name as hidden constants, and the key itself) are kept
   * but hidden once a credential is bound.
   */
  private static List<Property> authenticationProperties(ElementTemplate raw) {
    List<Property> result = new ArrayList<>();
    result.add(
        ConfigurationProperty.builder()
            .configurationTemplate(CREDENTIAL_ID)
            .id(CREDENTIAL_CHOOSER_ID)
            .label("Authentication credential")
            .tooltip(
                "Choose a reusable ElevenLabs credential, or configure the one-time API key below.")
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
                + ": the spec should declare exactly one security scheme");
      }
      var builder = property.toBuilder().condition(noCredential);
      if ("authentication.value.xi-api-key".equals(property.getId())) {
        builder.label("API key").tooltip(API_KEY_TOOLTIP).value(null).secret(true);
      }
      result.add(builder.build());
    }
    return result;
  }

  /**
   * One credential type for ElevenLabs only, so the chooser offers only ElevenLabs credentials. It
   * fixes the mechanism with hidden constants and saves into {@code
   * ServiceAuthenticationConfiguration}, like the other single-service http-json connectors.
   */
  static ConfigurationTemplate credentialType() {
    return new ConfigurationTemplate(
        CREDENTIAL_ID,
        "CREDENTIAL",
        1L,
        "ElevenLabs Authentication",
        List.of(
            credentialConstant("authentication.type", "apiKey"),
            credentialConstant("authentication.apiKeyLocation", "headers"),
            credentialConstant("authentication.name", "xi-api-key"),
            StringProperty.builder()
                .id("authentication.value")
                .label("API key")
                .tooltip(API_KEY_TOOLTIP)
                .group("authentication")
                .binding(new ConfigurationTemplateProperty("authentication.value"))
                .constraints(PropertyConstraints.builder().notEmpty(true).build())
                .secret(true)
                // Credential values are literals or secret references, never FEEL.
                .feel(FeelMode.disabled)
                .build()));
  }

  private static Property credentialConstant(String name, String value) {
    return HiddenProperty.builder()
        .id(name)
        .group("authentication")
        .value(value)
        .binding(new ConfigurationTemplateProperty(name))
        .build();
  }

  /**
   * Text to speech returns {@code audio/mpeg}, which the default JSON handling cannot read. Offer
   * http-json's response format for that operation only, defaulting to "Document reference". The
   * other operations return JSON and keep the default handling.
   */
  private static List<Property> responseFormatProperties() {
    Equals textToSpeech = new Equals(OPERATION_PROPERTY_ID, "text_to_speech_full");
    return List.of(
        DropdownProperty.builder()
            .choices(
                List.of(
                    new DropdownChoice("Document reference", "DOCUMENT"),
                    new DropdownChoice("as text", "TEXT"),
                    new DropdownChoice("as JSON", "JSON")))
            .id("documentReturnFormat")
            .label("Response format")
            .tooltip(
                "How the response body is returned. Document reference stores the generated audio"
                    + " in the document store and returns a reference to it in response.document.")
            .value("DOCUMENT")
            .group("output")
            .binding(new ZeebeInput("documentReturnFormat.choice"))
            .condition(textToSpeech)
            .build(),
        StringProperty.builder()
            .id("documentReturnFormatEncoding")
            .label("Encoding")
            .tooltip("Character set used to decode the response. Default UTF-8.")
            .value("UTF-8")
            .feel(FeelMode.optional)
            .group("output")
            .binding(new ZeebeInput("documentReturnFormat.encoding"))
            .condition(new AllMatch(textToSpeech, new Equals("documentReturnFormat", "TEXT")))
            .build());
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
   * @param bodyExample placeholder for the request body, or null if the operation has none
   * @param requiredBodyFields the required body fields, for the body tooltip
   * @param apiReference path of the operation's page under the ElevenLabs API reference
   */
  record Operation(
      String id,
      String name,
      String description,
      List<String> keywords,
      String bodyExample,
      String requiredBodyFields,
      String apiReference) {}
}
