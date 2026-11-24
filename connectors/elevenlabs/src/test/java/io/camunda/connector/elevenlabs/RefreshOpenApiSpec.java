/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.elevenlabs;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Refreshes the pinned ElevenLabs OpenAPI spec that {@link GenerateElementTemplate} reads.
 *
 * <p>The published spec has more than 500 operations on a single 2.8 MB line, so it is not
 * committed as-is. This keeps only the operations in {@link GenerateElementTemplate#OPERATION_IDS}
 * and the components they reference, unchanged, and writes them pretty-printed so a refresh shows a
 * readable diff. Camunda's own additions are not made here: they live in the overlay that {@link
 * GenerateElementTemplate} applies.
 *
 * <p>Run manually, then regenerate the template and review both diffs: {@code mvn -pl
 * connectors/elevenlabs test-compile exec:java
 * -Dexec.mainClass=io.camunda.connector.elevenlabs.RefreshOpenApiSpec -Dexec.classpathScope=test}
 */
public class RefreshOpenApiSpec {

  static final URI SPEC_URL = URI.create("https://api.elevenlabs.io/openapi.json");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static void main(String[] args) throws Exception {
    JsonNode published;
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpResponse<InputStream> response =
          client.send(
              HttpRequest.newBuilder(SPEC_URL).GET().build(),
              HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            "GET " + SPEC_URL + " returned HTTP " + response.statusCode());
      }
      try (InputStream body = response.body()) {
        published = MAPPER.readTree(body);
      }
    }

    ObjectNode trimmed = trim(published, GenerateElementTemplate.OPERATION_IDS);
    Path outputPath = GenerateElementTemplate.specPath();
    Files.writeString(
        outputPath, GenerateElementTemplate.prettyPrinter(MAPPER).writeValueAsString(trimmed));
    System.out.println("Wrote " + outputPath);
  }

  /**
   * Keeps {@code openapi}, {@code info}, the given operations (with any path-level parameters of
   * the paths they live on), and every component they reference directly or transitively. Fails if
   * an operation is missing, so a renamed or removed operation is noticed at refresh time.
   */
  static ObjectNode trim(JsonNode spec, Collection<String> operationIds) {
    ObjectNode trimmed = MAPPER.createObjectNode();
    trimmed.set("openapi", spec.get("openapi"));
    trimmed.set("info", spec.get("info"));

    ObjectNode paths = trimmed.putObject("paths");
    Set<String> found = new HashSet<>();
    for (Map.Entry<String, JsonNode> path : spec.path("paths").properties()) {
      ObjectNode keptPath = MAPPER.createObjectNode();
      for (Map.Entry<String, JsonNode> member : path.getValue().properties()) {
        String operationId = member.getValue().path("operationId").asText(null);
        if (operationId != null && operationIds.contains(operationId)) {
          keptPath.set(member.getKey(), member.getValue());
          found.add(operationId);
        }
      }
      if (!keptPath.isEmpty()) {
        if (path.getValue().has("parameters")) {
          keptPath.set("parameters", path.getValue().get("parameters"));
        }
        paths.set(path.getKey(), keptPath);
      }
    }

    Set<String> missing = new HashSet<>(operationIds);
    missing.removeAll(found);
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "The published spec no longer has the operations " + missing + ": update OPERATION_IDS");
    }

    Set<String> referenced = referencedComponents(spec, paths);
    ObjectNode components = trimmed.putObject("components");
    for (Map.Entry<String, JsonNode> type : spec.path("components").properties()) {
      ObjectNode keptType = MAPPER.createObjectNode();
      for (Map.Entry<String, JsonNode> component : type.getValue().properties()) {
        String ref = "#/components/" + escape(type.getKey()) + "/" + escape(component.getKey());
        if (referenced.contains(ref)) {
          keptType.set(component.getKey(), component.getValue());
        }
      }
      if (!keptType.isEmpty()) {
        components.set(type.getKey(), keptType);
      }
    }
    return trimmed;
  }

  private static Set<String> referencedComponents(JsonNode spec, JsonNode root) {
    Set<String> referenced = new HashSet<>();
    Deque<JsonNode> pending = new ArrayDeque<>();
    pending.push(root);
    while (!pending.isEmpty()) {
      for (JsonNode ref : pending.pop().findValues("$ref")) {
        String target = ref.asText();
        if (!target.startsWith("#/components/")) {
          throw new IllegalStateException("Unsupported reference " + target);
        }
        if (referenced.add(target)) {
          JsonNode component = spec.at(JsonPointer.compile(target.substring(1)));
          if (component.isMissingNode()) {
            throw new IllegalStateException("Unresolved reference " + target);
          }
          pending.push(component);
        }
      }
    }
    return referenced;
  }

  private static String escape(String referenceToken) {
    return referenceToken.replace("~", "~0").replace("/", "~1");
  }
}
