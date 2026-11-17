/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.annotation.value.SourceAware;
import io.camunda.client.annotation.value.SourceAware.FromDefaultProperty;
import io.camunda.client.annotation.value.SourceAware.FromOverrideProperty;
import org.junit.jupiter.api.Test;

class ManagedScriptJobWorkerCustomizerTest {

  private final ManagedScriptJobWorkerCustomizer customizer =
      new ManagedScriptJobWorkerCustomizer(8);

  @Test
  void replacesTheDefaultOfEveryWorker() {
    final var worker = worker(ManagedScriptFunction.JOB_TYPE, new FromDefaultProperty<>(32));

    customizer.customize(worker);

    assertThat(worker.getMaxJobsActive().value()).isEqualTo(8);
  }

  @Test
  void givesWayToAnOverrideOfTheJobType() {
    final var worker = worker(ManagedScriptFunction.JOB_TYPE, new FromOverrideProperty<>(4));

    customizer.customize(worker);

    assertThat(worker.getMaxJobsActive().value()).isEqualTo(4);
  }

  @Test
  void leavesTheWorkersOfOtherConnectorsAlone() {
    final var worker = worker("io.camunda:http-json:1", new FromDefaultProperty<>(32));

    customizer.customize(worker);

    assertThat(worker.getMaxJobsActive().value()).isEqualTo(32);
  }

  @Test
  void leavesTheWorkerAloneWithoutACap() {
    final var worker = worker(ManagedScriptFunction.JOB_TYPE, new FromDefaultProperty<>(32));

    new ManagedScriptJobWorkerCustomizer(null).customize(worker);

    assertThat(worker.getMaxJobsActive().value()).isEqualTo(32);
  }

  private static JobWorkerValue worker(String type, SourceAware<Integer> maxJobsActive) {
    final var worker = new JobWorkerValue();
    worker.setType(new SourceAware.FromAnnotation<>(type));
    worker.setMaxJobsActive(maxJobsActive);
    return worker;
  }
}
