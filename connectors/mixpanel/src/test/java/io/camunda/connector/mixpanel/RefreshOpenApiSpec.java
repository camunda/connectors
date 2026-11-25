/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.mixpanel;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Refreshes the pinned Mixpanel OpenAPI spec that {@link GenerateElementTemplate} reads.
 *
 * <p>Mixpanel publishes one YAML spec per API. The curated operations come from two of them, the
 * ingestion API and the query API, so this downloads both, keeps only the curated operations and
 * the components they reference, unchanged, and merges them into one pretty-printed JSON file so a
 * refresh shows a readable diff. The servers are not kept: the two APIs live on different hosts,
 * and the template builds each operation's URL itself.
 *
 * <p>Run manually, then regenerate the template and review both diffs: {@code mvn -pl
 * connectors/mixpanel test-compile exec:java
 * -Dexec.mainClass=io.camunda.connector.mixpanel.RefreshOpenApiSpec -Dexec.classpathScope=test}
 */
public class RefreshOpenApiSpec {

  private static final String SPECS_URL =
      "https://raw.githubusercontent.com/mixpanel/docs/main/openapi/";

  /** The published specs the curated operations come from. */
  static final List<String> SPEC_FILES = List.of("ingestion.openapi.yaml", "query.openapi.yaml");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static void main(String[] args) throws Exception {
    ObjectNode merged = null;
    try (HttpClient client = HttpClient.newHttpClient()) {
      for (String file : SPEC_FILES) {
        ObjectNode trimmed = trim(download(client, URI.create(SPECS_URL + file)));
        merged = merged == null ? trimmed : merge(merged, trimmed, file);
      }
    }

    Set<String> missing = new HashSet<>(GenerateElementTemplate.OPERATION_IDS);
    for (JsonNode path : merged.path("paths")) {
      for (JsonNode operation : path) {
        missing.remove(operation.path("operationId").asText());
      }
    }
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "The published specs no longer have the operations " + missing + ": update OPERATIONS");
    }

    Path outputPath = GenerateElementTemplate.specPath();
    Files.writeString(
        outputPath, GenerateElementTemplate.prettyPrinter(MAPPER).writeValueAsString(merged));
    System.out.println("Wrote " + outputPath);
  }

  private static JsonNode download(HttpClient client, URI url) throws Exception {
    HttpResponse<InputStream> response =
        client.send(
            HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
    if (response.statusCode() != 200) {
      throw new IllegalStateException("GET " + url + " returned HTTP " + response.statusCode());
    }
    try (InputStream body = response.body()) {
      return new YAMLMapper().readTree(body);
    }
  }

  /**
   * Keeps {@code openapi}, {@code info}, the curated operations in this spec, and every component
   * they reference directly or transitively. Global security requirements are copied onto each kept
   * operation that has none of its own, because the merged file has no global section.
   */
  static ObjectNode trim(JsonNode spec) {
    ObjectNode trimmed = MAPPER.createObjectNode();
    trimmed.set("openapi", spec.get("openapi"));
    trimmed.set("info", spec.get("info"));

    ObjectNode paths = trimmed.putObject("paths");
    for (Map.Entry<String, JsonNode> path : spec.path("paths").properties()) {
      ObjectNode keptPath = MAPPER.createObjectNode();
      for (Map.Entry<String, JsonNode> member : path.getValue().properties()) {
        String operationId = member.getValue().path("operationId").asText(null);
        if (operationId != null && GenerateElementTemplate.OPERATION_IDS.contains(operationId)) {
          ObjectNode operation = member.getValue().deepCopy();
          if (!operation.has("security") && spec.has("security")) {
            operation.set("security", spec.get("security"));
          }
          keptPath.set(member.getKey(), operation);
        }
      }
      if (!keptPath.isEmpty()) {
        if (path.getValue().has("parameters")) {
          keptPath.set("parameters", path.getValue().get("parameters"));
        }
        paths.set(path.getKey(), keptPath);
      }
    }

    Set<String> referenced = referencedComponents(spec, paths);
    ObjectNode components = trimmed.putObject("components");
    for (Map.Entry<String, JsonNode> type : spec.path("components").properties()) {
      ObjectNode keptType = MAPPER.createObjectNode();
      for (Map.Entry<String, JsonNode> component : type.getValue().properties()) {
        String ref = "#/components/" + escape(type.getKey()) + "/" + escape(component.getKey());
        if (referenced.contains(ref)
            || ("securitySchemes".equals(type.getKey()) && usesScheme(paths, component.getKey()))) {
          keptType.set(component.getKey(), component.getValue());
        }
      }
      if (!keptType.isEmpty()) {
        components.set(type.getKey(), keptType);
      }
    }
    return trimmed;
  }

  /**
   * Adds the paths and components of {@code other} to {@code base}. A path, or a component name,
   * that both specs define is only allowed when both definitions are identical.
   */
  static ObjectNode merge(ObjectNode base, JsonNode other, String otherName) {
    mergeMembers((ObjectNode) base.get("paths"), other.path("paths"), "path", otherName);
    ObjectNode components = (ObjectNode) base.get("components");
    for (Map.Entry<String, JsonNode> type : other.path("components").properties()) {
      if (!components.has(type.getKey())) {
        components.putObject(type.getKey());
      }
      mergeMembers(
          (ObjectNode) components.get(type.getKey()), type.getValue(), type.getKey(), otherName);
    }
    return base;
  }

  private static void mergeMembers(
      ObjectNode target, JsonNode source, String kind, String sourceName) {
    for (Map.Entry<String, JsonNode> member : source.properties()) {
      JsonNode existing = target.get(member.getKey());
      if (existing != null && !existing.equals(member.getValue())) {
        throw new IllegalStateException(
            sourceName + " redefines " + kind + " '" + member.getKey() + "' differently");
      }
      target.set(member.getKey(), member.getValue());
    }
  }

  private static boolean usesScheme(JsonNode paths, String scheme) {
    for (JsonNode path : paths) {
      for (JsonNode operation : path) {
        for (JsonNode requirement : operation.path("security")) {
          if (requirement.has(scheme)) {
            return true;
          }
        }
      }
    }
    return false;
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
