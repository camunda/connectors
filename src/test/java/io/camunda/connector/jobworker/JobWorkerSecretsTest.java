package io.camunda.connector.jobworker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.sdk.jobworker.JobHandlerWrapper;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import org.junit.Test;

public class JobWorkerSecretsTest {

  @Test
  public void shouldReplaceSecretsViaSpi() throws Exception {
    // given
    JobClient jobClient = mock(JobClient.class);
    CompleteJobCommandStep1 step1 = mock(CompleteJobCommandStep1.class, RETURNS_DEEP_STUBS);
    when(jobClient.newCompleteCommand(any())).thenReturn(step1);
    ActivatedJob job = mock(ActivatedJob.class);

    JobHandlerWrapper wrapper = new JobHandlerWrapper((context) -> {
      return context.getSecretStore().replaceSecret(JobWorkerTestSecretStore.SECRET_NAME);
    });

    // when
    wrapper.handle(jobClient, job);

    // then
    // the secret manager was loaded dynamically as SPI and replaced the secret
    verify(step1, times(1)).variables((Object) JobWorkerTestSecretStore.SECRET_VALUE);
  }
}
