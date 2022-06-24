package io.camunda.connector.sdk.jobworker;

import java.util.ServiceLoader;

import io.camunda.connector.sdk.common.ConnectorContext;
import io.camunda.connector.sdk.common.ConnectorFunction;
import io.camunda.connector.sdk.common.SecretStore;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;

public class JobHandlerWrapper implements JobHandler {

  private final ConnectorFunction call;

  public JobHandlerWrapper(ConnectorFunction call) {
    this.call = call;
  }

  @Override
  public void handle(JobClient client, ActivatedJob job) throws Exception {

    try {
      Object result = call.service(new JobHandlerInput(job));

      client.newCompleteCommand(job).variables(result).send().join();
    } catch (Exception error) {
      client.newFailCommand(job).retries(0).errorMessage(error.getMessage()).send().join();
    }
  }

  class JobHandlerInput implements ConnectorContext {

    private final ActivatedJob job;

    public JobHandlerInput(ActivatedJob job) {
      this.job = job;
    }

    @Override
    public <T extends Object> T getVariablesAsType(Class<T> cls) {
      return job.getVariablesAsType(cls);
    }

    @Override
    public SecretStore getSecretStore() {
      return ServiceLoader.load(SecretStore.class).findFirst().orElse(getEnvSecretStore());
    }

    protected SecretStore getEnvSecretStore() {
      return new SecretStore() {
        @Override
        public String replaceSecret(String value) {
          // TODO(nikku): provide basic "load from ENV" secret store
          throw new UnsupportedOperationException("Secrets not supported");
        }
      };
    }

  }

}
