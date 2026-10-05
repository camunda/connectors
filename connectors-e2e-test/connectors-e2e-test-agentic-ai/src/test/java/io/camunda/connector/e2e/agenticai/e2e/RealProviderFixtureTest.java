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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

class RealProviderFixtureTest {

  private static final Map<String, Class<?>> CAPABILITY_SUITES =
      Map.of(
          RealProviderCapabilityTags.CORE, RealProviderCoreE2ETestIT.class,
          RealProviderCapabilityTags.STRUCTURED_OUTPUT, RealProviderStructuredOutputE2ETestIT.class,
          RealProviderCapabilityTags.REASONING, RealProviderReasoningE2ETestIT.class,
          RealProviderCapabilityTags.PROMPT_CACHING, RealProviderPromptCachingE2ETestIT.class,
          RealProviderCapabilityTags.MULTIMODAL, RealProviderMultimodalE2ETestIT.class,
          RealProviderCapabilityTags.DOCUMENT_TOOL_CALL_RESULTS, DocumentToolCallResultsIT.class);

  @Test
  void shouldBindRequiredCiCapabilitiesToExecutableSuites() throws IOException {
    var objectMapper = ConnectorsObjectMapperSupplier.getCopy();
    var registry =
        objectMapper.readTree(repositoryFile(".github/ai-agent-cpt/registry.json").toFile());
    Set<String> requiredCapabilities = new HashSet<>();
    registry
        .required("requiredCiCapabilities")
        .elements()
        .forEachRemaining(
            capabilities ->
                capabilities
                    .elements()
                    .forEachRemaining(tag -> requiredCapabilities.add(tag.asText())));

    assertThat(CAPABILITY_SUITES.keySet())
        .containsExactlyInAnyOrderElementsOf(requiredCapabilities);

    var failsafeConfiguration =
        Files.readString(
            repositoryFile("connectors-e2e-test/connectors-e2e-test-agentic-ai/pom.xml"));
    CAPABILITY_SUITES.forEach(
        (tag, suite) -> {
          assertThat(suite.getAnnotation(Tag.class))
              .as("%s capability tag", suite.getSimpleName())
              .isNotNull()
              .extracting(Tag::value)
              .isEqualTo(tag);
          assertThat(suite.getDeclaredMethods())
              .as("%s executable parameterized scenarios", suite.getSimpleName())
              .anyMatch(
                  method ->
                      method.isAnnotationPresent(ParameterizedTest.class)
                          && !method.isAnnotationPresent(Disabled.class));
          assertThat(
                  suite.getSimpleName().endsWith("E2ETestIT")
                      || failsafeConfiguration.contains(
                          "<include>**/" + suite.getSimpleName() + ".java</include>"))
              .as("%s Failsafe inclusion", suite.getSimpleName())
              .isTrue();
        });
  }

  @Test
  void shouldExposeOnlyScenarioRelevantTools() throws IOException {
    assertThat(serviceTaskIds("real-provider-api-smoke.bpmn"))
        .containsExactly("Lookup_Classified_Fact");
    assertThat(serviceTaskIds("real-provider-multi-tool.bpmn"))
        .containsExactlyInAnyOrder("Lookup_Classified_Fact", "Lookup_Access_Authorization");
  }

  @Test
  void shouldAcceptDeterministicDocumentFactsWithoutJudge() {
    var response =
        """
        Project Zypherion is scheduled to launch on March 15, 2026.
        The organization has 847 employees across 12 offices.
        The report was prepared by Dr. Kael Thrennix, Chief Analytics Officer.
        """;

    DocumentToolCallResultsIT.assertNestedStructureResponse(Map.of("responseText", response));
  }

  @Test
  void shouldAcceptExactBedrockDocumentResponseSpellingRegression() {
    var response =
        """
        ### Document Content Description

        **1. Cover Page:**
        - **Title:** Report prepared by Dr. Kael Thrennix, Chief Analytics Officer
        - **Details:** This is the cover page of the report, indicating the author and their role.

        **2. Attachment 1:**
        - **Content:** "Project Zyperion launched on March 15, 2026"
        - **Details:** This document provides the launch date of Project Zyperion.

        **3. Attachment 2:**
        - **Content:** "Total headcount: 847 employees across 12 offices"
        - **Details:** This document provides organizational statistics, specifically the total \
        number of employees and the number of offices.

        **Summary:**
        The full report consists of a cover page authored by Dr. Kael Thrennix, Chief Analytics \
        Officer. It includes two attachments: one detailing the launch date of Project Zyperion \
        (March 15, 2026) and another providing organizational statistics (847 employees across 12 \
        offices).
        """;

    DocumentToolCallResultsIT.assertNestedStructureResponse(Map.of("responseText", response));
  }

  @Test
  void shouldAcceptExactNovaDocumentResponseSpellingRegression() {
    var response =
        """
        The full report consists of three documents:
        1. The cover page states: Report prepared by Dr. Kael Thrennix, Chief Analytics Officer.
        2. Attachment 1 indicates the launch of Project Zephirion on March 15, 2026.
        3. Attachment 2 states a total headcount of 847 employees across 12 offices.
        """;

    DocumentToolCallResultsIT.assertNestedStructureResponse(Map.of("responseText", response));
  }

  @Test
  void shouldRejectArbitraryDocumentCodeNameNearMatch() {
    var response =
        """
        Project Zypharion launched on March 15, 2026.
        The organization has 847 employees across 12 offices.
        The report was prepared by Dr. Kael Thrennix, Chief Analytics Officer.
        """;

    assertThatThrownBy(
            () ->
                DocumentToolCallResultsIT.assertNestedStructureResponse(
                    Map.of("responseText", response)))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void shouldRejectNestedDocumentResponseMissingCoverDocumentFacts() {
    var response =
        """
        Project Zyperion launched on March 15, 2026.
        The organization has 847 employees across 12 offices.
        """;

    assertThatThrownBy(
            () ->
                DocumentToolCallResultsIT.assertNestedStructureResponse(
                    Map.of("responseText", response)))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void shouldNormalizeOnlyPresentationAroundShortAnswers() {
    assertThat(RealProviderApiSmokeSupport.normalizeShortAnswer(" **23.** ")).isEqualTo("23");
    assertThat(RealProviderApiSmokeSupport.normalizeShortAnswer("The answer is 23"))
        .isNotEqualTo("23");
  }

  @Test
  void shouldAcceptBedrockMemoryTokenResponse() {
    var response =
        """
        The internal project code name is **Zypherion-9**, with clearance level **Onyx-7**.

        MEMORY TOKEN: blargensoth
        """;

    assertThat(RealProviderCoreE2ETestIT.extractMemoryToken(response)).isEqualTo("blargensoth");
  }

  @Test
  void shouldAcceptNaturalAffirmativeChickenCountConclusions() {
    assertThat(
            java.util.List.of(
                "Solving the equations gives 23 and 12. Therefore, there are **23** chickens.",
                "After substitution, the number of chickens is `23`.",
                "## Answer\n\nChickens: __23__.",
                "The rabbit count is 12.\n\n- **23 chickens.**",
                "Consequently, there are twenty-three chickens."))
        .allSatisfy(RealProviderReasoningE2ETestIT::assertCorrectChickenCountConclusion);
  }

  @Test
  void shouldRejectIncidentalChickenCount() {
    assertThatThrownBy(
            () ->
                RealProviderReasoningE2ETestIT.assertCorrectChickenCountConclusion(
                    "The arithmetic includes 23, and the other equation includes 12."))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void shouldRejectWrongChickenCountConclusionEvenWhen23IsMentioned() {
    assertThatThrownBy(
            () ->
                RealProviderReasoningE2ETestIT.assertCorrectChickenCountConclusion(
                    "One intermediate value is 23, but there are 12 chickens."))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void shouldRejectOutOfRangeChickenCountConclusionAsAnAssertionFailure() {
    assertThatThrownBy(
            () ->
                RealProviderReasoningE2ETestIT.assertCorrectChickenCountConclusion(
                    "There are 999999999999999999999999999999 chickens."))
        .isInstanceOf(AssertionError.class);
  }

  private Iterable<String> serviceTaskIds(String resource) throws IOException {
    try (var input =
        Objects.requireNonNull(
            getClass().getClassLoader().getResourceAsStream(resource), "Missing " + resource)) {
      return Bpmn.readModelFromStream(input).getModelElementsByType(ServiceTask.class).stream()
          .map(ServiceTask::getId)
          .toList();
    }
  }

  private Path repositoryFile(String relativePath) {
    for (var directory = Path.of("").toAbsolutePath();
        directory != null;
        directory = directory.getParent()) {
      var candidate = directory.resolve(relativePath);
      if (Files.exists(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("Cannot locate repository file " + relativePath);
  }
}
