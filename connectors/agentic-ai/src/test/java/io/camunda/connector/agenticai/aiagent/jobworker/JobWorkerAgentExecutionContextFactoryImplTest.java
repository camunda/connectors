/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.jobworker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretFilterFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobWorkerAgentExecutionContextFactoryImplTest {

  private static final SecretProvider SECRET_PROVIDER =
      new SecretProvider() {
        @Override
        public String getSecret(
            String name, io.camunda.connector.api.secret.SecretContext context) {
          return "s3cr3t";
        }
      };

  private final SecretFilterFactory secretFilterFactory = context -> SecretFilter.allowAll();

  private final JobWorkerAgentExecutionContextFactoryImpl factory =
      new JobWorkerAgentExecutionContextFactoryImpl(
          SECRET_PROVIDER,
          mock(ValidationProvider.class),
          mock(DocumentFactory.class),
          ConnectorsObjectMapperSupplier.getCopy(),
          secretFilterFactory);

  /**
   * The binding is where a secret reference becomes a value, so a failure raised anywhere after it
   * — including by the binding itself — can carry that value. Reporting it means redacting it, and
   * the store may hold a different value by then.
   */
  @Test
  void reportsTheValuesItSubstitutedEvenWhenTheBindingFails() {
    var job = mock(ActivatedJob.class);
    // resolves, then fails to map: provider is an object, not a string
    when(job.getVariables()).thenReturn("{\"provider\":\"{{secrets.TOKEN}}\"}");

    List<String> captured = new ArrayList<>();

    assertThatThrownBy(() -> factory.createExecutionContext(mock(JobClient.class), job, captured))
        .isInstanceOf(Exception.class);

    assertThat(captured).contains("s3cr3t");
  }
}
