/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class RealProviderResponseAssertions {

  private static final Pattern MEMORY_TOKEN =
      Pattern.compile("(?i)MEMORY TOKEN:\\s*([a-z]{8,})\\b");
  private static final String CHICKEN_COUNT = "(\\d+|twenty(?:-|\\s+)three)";
  private static final List<Pattern> CHICKEN_COUNT_CONCLUSIONS =
      List.of(
          Pattern.compile("(?i)\\bthere\\s+(?:are|were)\\s+" + CHICKEN_COUNT + "\\s+chickens?\\b"),
          Pattern.compile(
              "(?i)\\b(?:the\\s+)?(?:number|count)\\s+of\\s+chickens?\\s+"
                  + "(?:is|equals|=|:)\\s*"
                  + CHICKEN_COUNT
                  + "\\b"),
          Pattern.compile(
              "(?i)\\b(?:the\\s+farmer|the\\s+farm|we|he|she)\\s+"
                  + "(?:has|have)\\s+"
                  + CHICKEN_COUNT
                  + "\\s+chickens?\\b"),
          Pattern.compile("(?i)\\bchickens?\\s*(?:are|is|equals|=|:)\\s*" + CHICKEN_COUNT + "\\b"),
          Pattern.compile(
              "(?i)\\b(?:answer|therefore|thus|hence|so)\\s*(?:is|=|:|,)?\\s*"
                  + CHICKEN_COUNT
                  + "\\s+chickens?\\b"),
          Pattern.compile("(?im)^\\s*(?:[-+#>]\\s*)*" + CHICKEN_COUNT + "\\s+chickens?[.!]?\\s*$"));

  private RealProviderResponseAssertions() {}

  static void assertNestedDocumentResponse(Object agent) {
    var normalizedResponse = normalizedResponse(agent);
    // This scenario proves that documents nested at every level were extracted and consumed, not
    // character-perfect transcription of a static fabricated name. Real responses changed the
    // spelling while preserving every independently asserted fact, so accept only those observed
    // variants here rather than weakening the single- and multiple-document scenarios.
    assertProjectLaunchFacts(normalizedResponse, "zypherion", "zyperion", "zephirion");
    assertHeadcountFacts(normalizedResponse);
    assertThat(normalizedResponse)
        .as("normalized response containing author facts")
        .contains("kaelthrennix", "chiefanalyticsofficer");
  }

  static String normalizeShortAnswer(String text) {
    return text.strip().replaceAll("^[\\s`*_\"']+|[\\s`*_\"'.!]+$", "");
  }

  static String extractMemoryToken(String responseText) {
    final var matcher = MEMORY_TOKEN.matcher(responseText);
    assertThat(matcher.find()).as("turn 1 response contains an invented memory token").isTrue();
    return matcher.group(1);
  }

  static void assertCorrectChickenCountConclusion(String responseText) {
    final var markdownNormalized = responseText.replaceAll("[*_`]", "");
    final var statedCounts =
        CHICKEN_COUNT_CONCLUSIONS.stream()
            .flatMap(pattern -> pattern.matcher(markdownNormalized).results())
            .map(match -> match.group(1))
            .toList();

    assertThat(statedCounts)
        .as("affirmative chicken-count conclusions in response <%s>", responseText)
        .isNotEmpty()
        .allSatisfy(count -> assertThat(count).matches("(?i)(?:0*23|twenty(?:-|\\s+)three)"));
  }

  private static void assertProjectLaunchFacts(
      String normalizedResponse, String... acceptedProjectNameSpellings) {
    assertThat(normalizedResponse)
        .as("normalized response containing project launch facts")
        .containsAnyOf(acceptedProjectNameSpellings)
        .matches(".*(?:march15(?:th)?2026|15(?:th)?march2026|03152026|20260315).*");
  }

  private static void assertHeadcountFacts(String normalizedResponse) {
    assertThat(normalizedResponse)
        .as("normalized response containing headcount facts")
        .contains("847", "12");
  }

  private static String normalizedResponse(Object agent) {
    if (!(agent instanceof Map<?, ?> agentMap)) {
      throw new AssertionError("Expected agent result to be a map");
    }

    var responseText = agentMap.get("responseText");
    assertThat(responseText).as("agent responseText").isInstanceOf(String.class);

    return ((String) responseText).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
  }
}
