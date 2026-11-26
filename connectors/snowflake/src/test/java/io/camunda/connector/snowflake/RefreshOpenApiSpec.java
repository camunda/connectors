/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.snowflake;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Refreshes the pinned Snowflake SQL API spec that {@link GenerateElementTemplate} reads.
 *
 * <p>The spec is small, so it is committed verbatim: a refresh shows Snowflake's own diff.
 * Camunda's changes are not made here: they live in the overlay that {@link
 * GenerateElementTemplate} applies.
 *
 * <p>Run manually, then regenerate the template and review both diffs: {@code mvn -pl
 * connectors/snowflake test-compile exec:java
 * -Dexec.mainClass=io.camunda.connector.snowflake.RefreshOpenApiSpec -Dexec.classpathScope=test}
 */
public class RefreshOpenApiSpec {

  static final URI SPEC_URL =
      URI.create(
          "https://raw.githubusercontent.com/snowflakedb/snowflake-rest-api-specs/main/specifications/sqlapi.yaml");

  public static void main(String[] args) throws Exception {
    String spec;
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpResponse<String> response =
          client.send(
              HttpRequest.newBuilder(SPEC_URL).GET().build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            "GET " + SPEC_URL + " returned HTTP " + response.statusCode());
      }
      spec = response.body();
    }
    Path outputPath = GenerateElementTemplate.specPath();
    Files.writeString(outputPath, spec);
    System.out.println("Wrote " + outputPath);
  }
}
