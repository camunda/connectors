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

package io.camunda.connector.runtime.util.outbound;

import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.secret.SecretStore;
import io.camunda.connector.runtime.util.ConnectorHelper;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import java.util.Iterator;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@link JobHandler} that wraps an {@link OutboundConnectorFunction} */
public class ConnectorJobHandler implements JobHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorJobHandler.class);

  private final OutboundConnectorFunction call;

  /**
   * Create a handler wrapper for the specified connector function.
   *
   * @param call - the connector function to call
   */
  public ConnectorJobHandler(final OutboundConnectorFunction call) {
    this.call = call;
  }

  @Override
  public void handle(final JobClient client, final ActivatedJob job) {

    LOGGER.info("Received job {}", job.getKey());

    try {
      SecretStore secretStore = new SecretStore(getSecretProvider());
      Object result = call.execute(new JobHandlerContext(job, secretStore));

      client
          .newCompleteCommand(job)
          .variables(ConnectorHelper.createOutputVariables(result, job.getCustomHeaders()))
          .send()
          .join();

      LOGGER.debug("Completed job {}", job.getKey());
    } catch (Exception error) {

      LOGGER.error("Failed to process job {}", job.getKey(), error);

      client.newFailCommand(job).retries(0).errorMessage(error.getMessage()).send().join();
    }
  }

  protected SecretProvider getSecretProvider() {
    Iterator<SecretProvider> secretProviders = ServiceLoader.load(SecretProvider.class).iterator();
    if (!secretProviders.hasNext()) {
      getEnvSecretProvider();
    }
    return secretProviders.next();
  }

  protected SecretProvider getEnvSecretProvider() {
    return System::getenv;
  }
}
