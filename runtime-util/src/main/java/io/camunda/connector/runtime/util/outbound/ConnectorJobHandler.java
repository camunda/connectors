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

import io.camunda.connector.api.error.BpmnError;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.util.ConnectorHelper;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@link JobHandler} that wraps an {@link OutboundConnectorFunction} */
public class ConnectorJobHandler implements JobHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorJobHandler.class);

  protected final OutboundConnectorFunction call;

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

    final ConnectorResult result = new ConnectorResult();
    try {
      result.setResponseValue(call.execute(new JobHandlerContext(job, getSecretProvider())));
      result.setVariables(
          ConnectorHelper.createOutputVariables(result.getResponseValue(), job.getCustomHeaders()));

    } catch (Exception ex) {
      LOGGER.debug("Exception while processing job {}, error: {}", job.getKey(), ex);
      result.setResponseValue(Map.of("error", toMap(ex)));
      result.setException(ex);
    }

    try {
      ConnectorHelper.examineErrorExpression(result.getResponseValue(), job.getCustomHeaders())
          .ifPresentOrElse(
              error -> {
                LOGGER.debug(
                    "Throwing BPMN error for job {} with code {}", job.getKey(), error.getCode());
                throwBpmnError(client, job, error);
              },
              () -> {
                if (result.isSuccess()) {
                  LOGGER.debug("Completing job {}", job.getKey());
                  completeJob(client, job, result);
                } else {
                  logError(job, result.getException());
                  failJob(client, job, result.getException());
                }
              });
    } catch (Exception ex) {
      logError(job, ex);
      failJob(client, job, ex);
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

  protected void logError(ActivatedJob job, Exception ex) {
    LOGGER.error("Exception while processing job {}, error: {}", job.getKey(), ex);
  }

  protected void completeJob(JobClient client, ActivatedJob job, ConnectorResult result) {
    client.newCompleteCommand(job).variables(result.getVariables()).send().join();
  }

  protected void failJob(JobClient client, ActivatedJob job, Exception exception) {
    client.newFailCommand(job).retries(0).errorMessage(exception.getMessage()).send().join();
  }

  protected void throwBpmnError(JobClient client, ActivatedJob job, BpmnError value) {
    client
        .newThrowErrorCommand(job)
        .errorCode(value.getCode())
        .errorMessage(value.getMessage())
        .send()
        .join();
  }

  protected static Map<String, Object> toMap(Exception exception) {
    Map<String, Object> result = new HashMap<>();
    result.put("type", exception.getClass().getName());
    var message = exception.getMessage();
    if (message != null) {
      result.put("message", message);
    }
    if (exception instanceof ConnectorException) {
      var code = ((ConnectorException) exception).getErrorCode();
      if (code != null) {
        result.put("code", code);
      }
    }
    return Map.copyOf(result);
  }
}
