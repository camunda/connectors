package io.camunda.connector.runtime.jobworker;

import io.camunda.connector.api.ConnectorFunction;
import io.camunda.connector.api.SecretProvider;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class ConnectorJobHandlerSecretsTest {

  @Test
  public void shouldReplaceSecretsViaSpi() throws Exception {
    // given
    JobClient jobClient = Mockito.mock(JobClient.class);
    CompleteJobCommandStep1 step1 =
        Mockito.mock(CompleteJobCommandStep1.class, Mockito.RETURNS_DEEP_STUBS);

    Mockito.when(jobClient.newCompleteCommand(ArgumentMatchers.any())).thenReturn(step1);
    ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(-1l);

    ConnectorJobHandler wrapper =
        new ConnectorJobHandler(
            (context) -> {
              return context.getSecretStore().replaceSecret("secrets." + TestSecretProvider.SECRET_NAME);
            });

    // when
    wrapper.handle(jobClient, job);

    // then
    // the secret provider was loaded dynamically as SPI and replaced the secret
    Mockito.verify(step1, Mockito.times(1)).variables((Object) TestSecretProvider.SECRET_VALUE);
  }

  @Test
  public void shouldOverrideSecretProvider() {
    // given
    JobClient jobClient = Mockito.mock(JobClient.class);
    CompleteJobCommandStep1 step1 =
        Mockito.mock(CompleteJobCommandStep1.class, Mockito.RETURNS_DEEP_STUBS);

    Mockito.when(jobClient.newCompleteCommand(ArgumentMatchers.any())).thenReturn(step1);
    ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(-1l);

    ConnectorJobHandler wrapper =
        new TestConnectorJobHandler(
            (context) -> {
              return context.getSecretStore().replaceSecret("secrets.BAR");
            });

    // when
    wrapper.handle(jobClient, job);

    // then
    // the custom secret provider was used
    Mockito.verify(step1, Mockito.times(1)).variables((Object) "baz");
  }

  private static class TestConnectorJobHandler extends ConnectorJobHandler {

    public TestConnectorJobHandler(ConnectorFunction call) {
      super(call);
    }

    @Override
    public SecretProvider getSecretProvider() {
      return name -> "BAR".equals(name) ? "baz" : null;
    }
  }
}
