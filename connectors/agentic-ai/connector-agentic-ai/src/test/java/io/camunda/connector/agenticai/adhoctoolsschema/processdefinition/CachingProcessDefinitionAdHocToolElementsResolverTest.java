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
    when(delegate.resolveToolElements(null, PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1))
        .thenReturn(resolvedElements);

    final var response1 =
        resolver.resolveToolElements(null, PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1);
    final var response2 =
        resolver.resolveToolElements(null, PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1);

    assertThat(response1).isSameAs(response2).isSameAs(resolvedElements);

    verify(delegate, times(1))
        .resolveToolElements(null, PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1);
    verifyNoMoreInteractions(delegate);
  }

  /**
   * Process definition keys are only unique within one orchestration cluster, so the same key
   * identifies a different definition on each of them. Caching on the key alone would serve one
   * physical tenant's tool elements to another.
   */
  @Test
  void resolvesSeparatelyPerPhysicalTenantForTheSameProcessDefinitionKey() {
    final var tenantAElements = elements("tenant-a-element");
    final var tenantBElements = elements("tenant-b-element");
    when(delegate.resolveToolElements("tenanta", PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1))
        .thenReturn(tenantAElements);
    when(delegate.resolveToolElements("tenantb", PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1))
        .thenReturn(tenantBElements);

    final var tenantA =
        resolver.resolveToolElements("tenanta", PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1);
    final var tenantB =
        resolver.resolveToolElements("tenantb", PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1);

    assertThat(tenantA).isSameAs(tenantAElements);
    assertThat(tenantB).isSameAs(tenantBElements);

    verify(delegate, times(1))
        .resolveToolElements("tenanta", PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1);
    verify(delegate, times(1))
        .resolveToolElements("tenantb", PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1);
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void cachesPerPhysicalTenant() {
    final var resolvedElements = elements("element1");
    when(delegate.resolveToolElements("tenanta", PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1))
        .thenReturn(resolvedElements);

    final var first =
        resolver.resolveToolElements("tenanta", PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1);
    final var second =
        resolver.resolveToolElements("tenanta", PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1);

    assertThat(first).isSameAs(second).isSameAs(resolvedElements);
    verify(delegate, times(1))
        .resolveToolElements("tenanta", PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1);
    verifyNoMoreInteractions(delegate);
  }

  @ParameterizedTest
  @MethodSource("differentCacheKeys")
  void returnsDifferentValueForDifferentCacheKey(
      Pair<Long, String> cacheKey1, Pair<Long, String> cacheKey2) {
    final var resolvedElements1 = elements("element1", "element3");
    final var resolvedElements2 = elements("element2");

    when(delegate.resolveToolElements(null, cacheKey1.getLeft(), cacheKey1.getRight()))
        .thenReturn(resolvedElements1);
    when(delegate.resolveToolElements(null, cacheKey2.getLeft(), cacheKey2.getRight()))
        .thenReturn(resolvedElements2);

    final var response1 =
        resolver.resolveToolElements(null, cacheKey1.getLeft(), cacheKey1.getRight());
    final var response2 =
        resolver.resolveToolElements(null, cacheKey2.getLeft(), cacheKey2.getRight());

    assertThat(response1).isNotSameAs(response2).isSameAs(resolvedElements1);
    assertThat(response2).isNotSameAs(response1).isSameAs(resolvedElements2);

    verify(delegate, times(1)).resolveToolElements(null, cacheKey1.getLeft(), cacheKey1.getRight());
    verify(delegate, times(1)).resolveToolElements(null, cacheKey2.getLeft(), cacheKey2.getRight());
    verifyNoMoreInteractions(delegate);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = {0, -10})
  void throwsExceptionWhenProcessDefinitionKeyIsInvalid(Long processDefinitionKey) {
    assertThatThrownBy(
            () -> resolver.resolveToolElements(null, processDefinitionKey, AD_HOC_SUB_PROCESS_ID_1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Process definition key must not be null or negative");

    verifyNoInteractions(delegate);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void throwsExceptionWhenAdHocSubProcessIdIsInvalid(String adHocSubProcessId) {
    assertThatThrownBy(
            () -> resolver.resolveToolElements(null, PROCESS_DEFINITION_KEY_1, adHocSubProcessId))
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
