/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;
import java.time.Duration;
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
class CachingAdHocToolsSchemaResolverTest {

  private static final Long PROCESS_DEFINITION_KEY_1 = 123456L;
  private static final Long PROCESS_DEFINITION_KEY_2 = 654321L;

  private static final String AD_HOC_SUB_PROCESS_ID_1 = "AHSP_1";
  private static final String AD_HOC_SUB_PROCESS_ID_2 = "AHSP_2";

  @Mock private AdHocToolsSchemaResolver delegate;
  private CachingAdHocToolsSchemaResolver resolver;

  @BeforeEach
  void setUp() {
    resolver =
        new CachingAdHocToolsSchemaResolver(
            delegate,
            new CachingAdHocToolsSchemaResolver.CacheConfiguration(10L, Duration.ofHours(1)));
  }

  @Test
  void returnsCachedValue() {
    final var mockedResponse = mock(AdHocToolsSchemaResponse.class);
    when(delegate.resolveSchema(PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1))
        .thenReturn(mockedResponse);

    final var response1 = resolver.resolveSchema(PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1);
    final var response2 = resolver.resolveSchema(PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1);

    assertThat(response1).isSameAs(response2).isSameAs(mockedResponse);

    verify(delegate, times(1)).resolveSchema(PROCESS_DEFINITION_KEY_1, AD_HOC_SUB_PROCESS_ID_1);
    verifyNoMoreInteractions(delegate);
  }

  @ParameterizedTest
  @MethodSource("differentCacheKeys")
  void returnsDifferentValueForDifferentCacheKey(
      Pair<Long, String> cacheKey1, Pair<Long, String> cacheKey2) {
    final var mockedResponse1 = mock(AdHocToolsSchemaResponse.class);
    final var mockedResponse2 = mock(AdHocToolsSchemaResponse.class);

    when(delegate.resolveSchema(cacheKey1.getLeft(), cacheKey1.getRight()))
        .thenReturn(mockedResponse1);
    when(delegate.resolveSchema(cacheKey2.getLeft(), cacheKey2.getRight()))
        .thenReturn(mockedResponse2);

    final var response1 = resolver.resolveSchema(cacheKey1.getLeft(), cacheKey1.getRight());
    final var response2 = resolver.resolveSchema(cacheKey2.getLeft(), cacheKey2.getRight());

    assertThat(response1).isNotSameAs(response2).isSameAs(mockedResponse1);
    assertThat(response2).isNotSameAs(response1).isSameAs(mockedResponse2);

    verify(delegate, times(1)).resolveSchema(cacheKey1.getLeft(), cacheKey1.getRight());
    verify(delegate, times(1)).resolveSchema(cacheKey2.getLeft(), cacheKey2.getRight());
    verifyNoMoreInteractions(delegate);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = {0, -10})
  void throwsExceptionWhenProcessDefinitionKeyIsInvalid(Long processDefinitionKey) {
    assertThatThrownBy(() -> resolver.resolveSchema(processDefinitionKey, AD_HOC_SUB_PROCESS_ID_1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Process definition key must not be null or negative");

    verifyNoInteractions(delegate);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void throwsExceptionWhenAdHocSubProcessIdIsInvalid(String adHocSubProcessId) {
    assertThatThrownBy(() -> resolver.resolveSchema(PROCESS_DEFINITION_KEY_1, adHocSubProcessId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("adHocSubProcessId cannot be null or empty");

    verifyNoInteractions(delegate);
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
