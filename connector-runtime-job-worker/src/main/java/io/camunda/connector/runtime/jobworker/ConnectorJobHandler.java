package io.camunda.connector.runtime.jobworker;

import io.camunda.connector.sdk.ConnectorContext;
import io.camunda.connector.sdk.ConnectorFunction;
import io.camunda.connector.sdk.SecretProvider;
import io.camunda.connector.sdk.SecretStore;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectorJobHandler implements JobHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorJobHandler.class);

  private final ConnectorFunction call;

  public ConnectorJobHandler(ConnectorFunction call) {
    this.call = call;
  }

  @Override
  public void handle(JobClient client, ActivatedJob job) {

    LOGGER.info("Received job {}", job.getKey());

    try {
      Object result = call.execute(new JobHandlerContext(job));

      client.newCompleteCommand(job).variables(result).send().join();

      LOGGER.debug("Completed job {}", job.getKey());
    } catch (Exception error) {

      LOGGER.error("Failed to process job {}", job.getKey(), error);

      client.newFailCommand(job).retries(0).errorMessage(error.getMessage()).send().join();
    }
  }

  protected SecretProvider getSecretProvider() {
    return ServiceLoader.load(SecretProvider.class).findFirst().orElse(getEnvSecretProvider());
  }

  protected SecretProvider getEnvSecretProvider() {
    return new SecretProvider() {
      @Override
      public String getSecret(String value) {
        return System.getenv(value);
      }
    };
  }

  class JobHandlerContext implements ConnectorContext {

    private final ActivatedJob job;

    public JobHandlerContext(ActivatedJob job) {
      this.job = job;
    }

    @Override
    public SecretStore getSecretStore() {
      return new SecretStore(ConnectorJobHandler.this.getSecretProvider());
    }

    @Override
    public <T extends Object> T getVariablesAsType(Class<T> cls) {
      return job.getVariablesAsType(cls);
    }

    @Override
    public String getVariables() {
      return job.getVariables();
    }
  }
}
