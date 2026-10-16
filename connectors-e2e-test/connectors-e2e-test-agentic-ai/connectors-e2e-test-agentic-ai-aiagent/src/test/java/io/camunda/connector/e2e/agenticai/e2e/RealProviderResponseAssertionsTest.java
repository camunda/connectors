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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RealProviderResponseAssertionsTest {

  @Test
  void shouldAcceptDeterministicDocumentFactsWithoutJudge() {
    var response =
        """
        Project Zypherion is scheduled to launch on March 15, 2026.
        The organization has 847 employees across 12 offices.
        The report was prepared by Dr. Kael Thrennix, Chief Analytics Officer.
        """;

    RealProviderResponseAssertions.assertNestedDocumentResponse(Map.of("responseText", response));
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

    RealProviderResponseAssertions.assertNestedDocumentResponse(Map.of("responseText", response));
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

    RealProviderResponseAssertions.assertNestedDocumentResponse(Map.of("responseText", response));
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
                RealProviderResponseAssertions.assertNestedDocumentResponse(
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
                RealProviderResponseAssertions.assertNestedDocumentResponse(
                    Map.of("responseText", response)))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void shouldNormalizeOnlyPresentationAroundShortAnswers() {
    assertThat(RealProviderResponseAssertions.normalizeShortAnswer(" **23.** ")).isEqualTo("23");
    assertThat(RealProviderResponseAssertions.normalizeShortAnswer("The answer is 23"))
        .isNotEqualTo("23");
  }

  @Test
  void shouldAcceptBedrockMemoryTokenResponse() {
    var response =
        """
        The internal project code name is **Zypherion-9**, with clearance level **Onyx-7**.

        MEMORY TOKEN: blargensoth
        """;

    assertThat(RealProviderResponseAssertions.extractMemoryToken(response))
        .isEqualTo("blargensoth");
  }

  @Test
  void shouldAcceptNaturalAffirmativeChickenCountConclusions() {
    assertThat(
            List.of(
                "Solving the equations gives 23 and 12. Therefore, there are **23** chickens.",
                "After substitution, the number of chickens is `23`.",
                "## Answer\n\nChickens: __23__.",
                "The rabbit count is 12.\n\n- **23 chickens.**",
                "Consequently, there are twenty-three chickens."))
        .allSatisfy(RealProviderResponseAssertions::assertCorrectChickenCountConclusion);
  }

  @Test
  void shouldRejectIncidentalChickenCount() {
    assertThatThrownBy(
            () ->
                RealProviderResponseAssertions.assertCorrectChickenCountConclusion(
                    "The arithmetic includes 23, and the other equation includes 12."))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void shouldRejectWrongChickenCountConclusionEvenWhen23IsMentioned() {
    assertThatThrownBy(
            () ->
                RealProviderResponseAssertions.assertCorrectChickenCountConclusion(
                    "One intermediate value is 23, but there are 12 chickens."))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void shouldRejectOutOfRangeChickenCountConclusionAsAnAssertionFailure() {
    assertThatThrownBy(
            () ->
                RealProviderResponseAssertions.assertCorrectChickenCountConclusion(
                    "There are 999999999999999999999999999999 chickens."))
        .isInstanceOf(AssertionError.class);
  }
}
