/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.jobworker;

import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.ConnectorFunction;
import io.camunda.connector.api.ConnectorInput;
import io.camunda.connector.api.SecretProvider;
import io.camunda.connector.api.SecretStore;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Job worker handler wrapper for a connector function */
public class ConnectorJobHandler implements JobHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorJobHandler.class);

  private final ConnectorFunction call;

  /**
   * Create a handler wrapper for the specified connector function.
   *
   * @param call - the connector function to call
   */
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

  protected class JobHandlerContext implements ConnectorContext {

    private final ActivatedJob job;
    private SecretStore secretStore;

    public JobHandlerContext(ActivatedJob job) {
      this.job = job;
    }

    @Override
    public void replaceSecrets(ConnectorInput input) {
      input.replaceSecrets(getSecretStore());
    }

    @Override
    public SecretStore getSecretStore() {
      if (secretStore == null) {
        secretStore = new SecretStore(ConnectorJobHandler.this.getSecretProvider());
      }
      return secretStore;
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
