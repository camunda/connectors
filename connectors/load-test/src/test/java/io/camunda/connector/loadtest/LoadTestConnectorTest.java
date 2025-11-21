/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.loadtest.model.LoadTestRequest;
import io.camunda.connector.loadtest.model.RandomLoadTestRequest;
import org.junit.jupiter.api.Test;

class LoadTestConnectorTest {

  private final LoadTestConnector connector = new LoadTestConnector();

  @Test
  void shouldPerformIoWaitOnly() {
    // given
    var request = new LoadTestRequest(50L, null);

    // when
    var result = connector.loadTest(request);

    // then
    assertThat(result).isNotNull();
    assertThat(result.requestedIoWaitMs()).isEqualTo(50L);
    assertThat(result.requestedCpuBurnMs()).isEqualTo(0L);
    assertThat(result.actualIoWaitMs()).isGreaterThanOrEqualTo(45L); // Allow some tolerance
    assertThat(result.actualCpuBurnMs()).isEqualTo(0L);
    assertThat(result.accumulator()).isEqualTo(0L);
  }

  @Test
  void shouldPerformCpuBurnOnly() {
    // given
    var request = new LoadTestRequest(null, 50L);

    // when
    var result = connector.loadTest(request);

    // then
    assertThat(result).isNotNull();
    assertThat(result.requestedIoWaitMs()).isEqualTo(0L);
    assertThat(result.requestedCpuBurnMs()).isEqualTo(50L);
    assertThat(result.actualIoWaitMs()).isEqualTo(0L);
    assertThat(result.actualCpuBurnMs()).isGreaterThanOrEqualTo(45L); // Allow some tolerance
    assertThat(result.accumulator()).isNotEqualTo(0L); // Should have computed something
  }

  @Test
  void shouldPerformBothIoWaitAndCpuBurn() {
    // given
    var request = new LoadTestRequest(30L, 30L);

    // when
    var result = connector.loadTest(request);

    // then
    assertThat(result).isNotNull();
    assertThat(result.requestedIoWaitMs()).isEqualTo(30L);
    assertThat(result.requestedCpuBurnMs()).isEqualTo(30L);
    assertThat(result.actualIoWaitMs()).isGreaterThanOrEqualTo(25L);
    assertThat(result.actualCpuBurnMs()).isGreaterThanOrEqualTo(25L);
    assertThat(result.accumulator()).isNotEqualTo(0L);
  }

  @Test
  void shouldCapDurationAtMaximum() {
    // given
    var request = new LoadTestRequest(15_000L, 15_000L);

    // when
    var result = connector.loadTest(request);

    // then - should be capped at 10_000ms
    assertThat(result).isNotNull();
    assertThat(result.requestedIoWaitMs()).isEqualTo(10_000L);
    assertThat(result.requestedCpuBurnMs()).isEqualTo(10_000L);
  }

  @Test
  void shouldHandleNullValues() {
    // given
    var request = new LoadTestRequest(null, null);

    // when
    var result = connector.loadTest(request);

    // then
    assertThat(result).isNotNull();
    assertThat(result.requestedIoWaitMs()).isEqualTo(0L);
    assertThat(result.requestedCpuBurnMs()).isEqualTo(0L);
    assertThat(result.actualIoWaitMs()).isEqualTo(0L);
    assertThat(result.actualCpuBurnMs()).isEqualTo(0L);
    assertThat(result.accumulator()).isEqualTo(0L);
  }

  @Test
  void shouldHandleZeroValues() {
    // given
    var request = new LoadTestRequest(0L, 0L);

    // when
    var result = connector.loadTest(request);

    // then
    assertThat(result).isNotNull();
    assertThat(result.requestedIoWaitMs()).isEqualTo(0L);
    assertThat(result.requestedCpuBurnMs()).isEqualTo(0L);
    assertThat(result.actualIoWaitMs()).isEqualTo(0L);
    assertThat(result.actualCpuBurnMs()).isEqualTo(0L);
    assertThat(result.accumulator()).isEqualTo(0L);
  }

  @Test
  void shouldPerformRandomLoadTestWithinBounds() {
    // given
    var request = new RandomLoadTestRequest(10L, 50L, 10L, 50L);

    // when
    var result = connector.randomLoadTest(request);

    // then
    assertThat(result).isNotNull();
    assertThat(result.requestedIoWaitMs()).isBetween(10L, 50L);
    assertThat(result.requestedCpuBurnMs()).isBetween(10L, 50L);
    assertThat(result.actualIoWaitMs()).isGreaterThanOrEqualTo(0L);
    assertThat(result.actualCpuBurnMs()).isGreaterThanOrEqualTo(0L);
  }

  @Test
  void shouldHandleRandomLoadTestWithOnlyIoBounds() {
    // given
    var request = new RandomLoadTestRequest(20L, 40L, null, null);

    // when
    var result = connector.randomLoadTest(request);

    // then
    assertThat(result).isNotNull();
    assertThat(result.requestedIoWaitMs()).isBetween(20L, 40L);
    assertThat(result.requestedCpuBurnMs()).isEqualTo(0L);
  }

  @Test
  void shouldHandleRandomLoadTestWithOnlyCpuBounds() {
    // given
    var request = new RandomLoadTestRequest(null, null, 20L, 40L);

    // when
    var result = connector.randomLoadTest(request);

    // then
    assertThat(result).isNotNull();
    assertThat(result.requestedIoWaitMs()).isEqualTo(0L);
    assertThat(result.requestedCpuBurnMs()).isBetween(20L, 40L);
  }

  @Test
  void shouldCapRandomLoadTestAtMaximum() {
    // given
    var request = new RandomLoadTestRequest(5_000L, 15_000L, 5_000L, 15_000L);

    // when
    var result = connector.randomLoadTest(request);

    // then - should be capped at 10_000ms
    assertThat(result).isNotNull();
    assertThat(result.requestedIoWaitMs()).isLessThanOrEqualTo(10_000L);
    assertThat(result.requestedCpuBurnMs()).isLessThanOrEqualTo(10_000L);
  }
}
