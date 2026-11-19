/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import io.camunda.client.annotation.customizer.JobWorkerValueCustomizer;
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.annotation.value.SourceAware.FromAnnotation;

/**
 * Sets the jobs a managed-script worker holds at once, hence the scripts it runs at once. Jobs
 * beyond it stay with the broker, their retries untouched. One worker serves each physical tenant,
 * so the cap applies to each of them, on each runtime instance.
 *
 * <p>The cap has the priority of an annotation value, whichever order the customizers run in: it
 * replaces a default from {@code camunda.client.worker.defaults}, and gives way to an override of
 * the job type in {@code camunda.client.worker.override}.
 */
final class ManagedScriptJobWorkerCustomizer implements JobWorkerValueCustomizer {

  private final Integer maxJobsActive;

  /**
   * @param maxJobsActive {@code null} to leave the worker as any other connector's
   */
  ManagedScriptJobWorkerCustomizer(Integer maxJobsActive) {
    this.maxJobsActive = maxJobsActive;
  }

  @Override
  public void customize(JobWorkerValue jobWorkerValue) {
    if (maxJobsActive == null
        || jobWorkerValue.getType() == null
        || !ManagedScriptFunction.JOB_TYPE.equals(jobWorkerValue.getType().value())) {
      return;
    }
    final var cap = new FromAnnotation<>(maxJobsActive);
    final var current = jobWorkerValue.getMaxJobsActive();
    if (current == null || current.priority() <= cap.priority()) {
      jobWorkerValue.setMaxJobsActive(cap);
    }
  }
}
