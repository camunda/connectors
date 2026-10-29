/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The script and optional dependency manifest resolved by the engine for one job. */
record LinkedResources(long scriptResourceKey, Optional<Long> dependenciesResourceKey) {

  static final String HEADER = "linkedResources";
  static final String SCRIPT_LINK = "script";
  static final String DEPENDENCIES_LINK = "dependencies";

  /**
   * Parses the {@code linkedResources} job header, a JSON array of {@code {"resourceKey",
   * "resourceType", "linkName"}} objects.
   *
   * @throws InvalidJobException when the header is missing, malformed or has no script link
   */
  static LinkedResources parse(Map<String, String> headers, ObjectMapper objectMapper) {
    final var header = headers.get(HEADER);
    if (header == null || header.isBlank()) {
      throw new InvalidJobException(
          "Missing '%s' header: the managed script is not linked to the task".formatted(HEADER));
    }
    final List<Link> links;
    try {
      // Strict: the shared mapper would accept a single object as an array, a one-element array as
      // a single value, and trailing tokens.
      links =
          objectMapper
              .readerFor(new TypeReference<List<Link>>() {})
              .without(
                  DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
                  DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)
              .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
              .readValue(header);
    } catch (JsonProcessingException e) {
      throw new InvalidJobException("The '%s' header is not a valid JSON array".formatted(HEADER));
    }
    final var script =
        resourceKey(links, SCRIPT_LINK)
            .orElseThrow(
                () ->
                    new InvalidJobException(
                        "The '%s' header has no '%s' link".formatted(HEADER, SCRIPT_LINK)));
    return new LinkedResources(script, resourceKey(links, DEPENDENCIES_LINK));
  }

  private static Optional<Long> resourceKey(List<Link> links, String linkName) {
    final var matches =
        links == null
            ? List.<Link>of()
            : links.stream()
                .filter(link -> link != null && linkName.equals(link.linkName()))
                .toList();
    if (matches.isEmpty()) {
      return Optional.empty();
    }
    if (matches.size() > 1) {
      throw new InvalidJobException(
          "The '%s' header has more than one '%s' link".formatted(HEADER, linkName));
    }
    final var resourceKey = matches.getFirst().resourceKey();
    final long key;
    try {
      key = Long.parseLong(resourceKey);
    } catch (NumberFormatException e) {
      throw invalidKey(linkName, resourceKey);
    }
    // Engine keys are positive: 0 or a negative value identifies no deployed resource.
    if (key <= 0) {
      throw invalidKey(linkName, resourceKey);
    }
    return Optional.of(key);
  }

  private static InvalidJobException invalidKey(String linkName, String resourceKey) {
    return new InvalidJobException(
        "The '%s' link has an invalid resourceKey '%s'".formatted(linkName, resourceKey));
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Link(String resourceKey, String resourceType, String linkName) {}
}
