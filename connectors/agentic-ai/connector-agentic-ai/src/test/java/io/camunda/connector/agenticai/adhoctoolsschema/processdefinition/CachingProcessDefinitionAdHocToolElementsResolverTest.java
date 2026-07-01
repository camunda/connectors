/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.processdefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CachingProcessDefinitionAdHocToolElementsResolverTest {

  private static final Long PROCESS_DEFINITION_KEY_1 = 123456L;
  private static final Long PROCESS_DEFINITION_KEY_2 = 654321L;

  private static final String AD_HOC_SUB_PROCESS_ID_1 = "AHSP_1";
  private static final String AD_HOC_SUB_PROCESS_ID_2 = "AHSP_2";

  @Mock private ProcessDefinitionAdHocToolElementsResolver delegate;
  private CachingProcessDefinitionAdHocToolElementsResolver resolver;

  @BeforeEach
  void setUp() {
    resolver =
        new CachingProcessDefinitionAdHocToolElementsResolver(
            delegate,
            new CachingProcessDefinitionAdHocToolElementsResolver.CacheConfiguration(
                10L, Duration.ofHours(1)));
  }

  @Test
  void returnsCachedValue() {
    final var resolvedElements = elements("element1");
    when(delegate.resolveToolElements(PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1))
        .thenReturn(resolvedElements);

    final var response1 =
        resolver.resolveToolElements(PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1);
    final var response2 =
        resolver.resolveToolElements(PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1);

    assertThat(response1).isSameAs(response2).isSameAs(resolvedElements);

    verify(delegate, times(1))
        .resolveToolElements(PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1);
    verifyNoMoreInteractions(delegate);
  }

  @ParameterizedTest
  @MethodSource("differentCacheKeys")
  void returnsDifferentValueForDifferentCacheKey(
      Pair<Long, String> cacheKey1, Pair<Long, String> cacheKey2) {
    final var resolvedElements1 = elements("element1", "element3");
    final var resolvedElements2 = elements("element2");

    when(delegate.resolveToolElements(cacheKey1.getLeft(), cacheKey1.getRight()))
        .thenReturn(resolvedElements1);
    when(delegate.resolveToolElements(cacheKey2.getLeft(), cacheKey2.getRight()))
        .thenReturn(resolvedElements2);

    final var response1 = resolver.resolveToolElements(cacheKey1.getLeft(), cacheKey1.getRight());
    final var response2 = resolver.resolveToolElements(cacheKey2.getLeft(), cacheKey2.getRight());

    assertThat(response1).isNotSameAs(response2).isSameAs(resolvedElements1);
    assertThat(response2).isNotSameAs(response1).isSameAs(resolvedElements2);

    verify(delegate, times(1)).resolveToolElements(cacheKey1.getLeft(), cacheKey1.getRight());
    verify(delegate, times(1)).resolveToolElements(cacheKey2.getLeft(), cacheKey2.getRight());
    verifyNoMoreInteractions(delegate);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = {0, -10})
  void throwsExceptionWhenProcessDefinitionKeyIsInvalid(Long processDefinitionKey) {
    assertThatThrownBy(
            () -> resolver.resolveToolElements(processDefinitionKey, AD_HOC_SUB_PROCESS_ID_1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Process definition key must not be null or negative");

    verifyNoInteractions(delegate);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void throwsExceptionWhenAdHocSubProcessIdIsInvalid(String adHocSubProcessId) {
    assertThatThrownBy(
            () -> resolver.resolveToolElements(PROCESS_DEFINITION_KEY_1, adHocSubProcessId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("adHocSubProcessId cannot be null or empty");

    verifyNoInteractions(delegate);
  }

  private List<AdHocToolElement> elements(String... elementIds) {
    return Arrays.stream(elementIds)
        .map(
            elementId ->
                AdHocToolElement.builder()
                    .elementId(elementId)
                    .elementName("Element Name " + elementId)
                    .build())
        .toList();
  }

  static Stream<Arguments> differentCacheKeys() {
    return Stream.of(
        arguments(
            Pair.of(PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1),
            Pair.of(PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_2)),
        arguments(
            Pair.of(PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1),
            Pair.of(PROCESS_DEFINITION_KEY_2, AD_HOC_SUB_PROCESS_ID_1)));
  }
}
