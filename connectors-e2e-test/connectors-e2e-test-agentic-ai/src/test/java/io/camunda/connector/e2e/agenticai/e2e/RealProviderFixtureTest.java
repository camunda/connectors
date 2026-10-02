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

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class RealProviderFixtureTest {

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
}
