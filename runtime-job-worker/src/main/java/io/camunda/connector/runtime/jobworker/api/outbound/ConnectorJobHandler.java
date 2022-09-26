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

package io.camunda.connector.runtime.jobworker.api.outbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.secret.SecretStore;
import io.camunda.connector.impl.outbound.AbstractOutboundConnectorContext;
import io.camunda.connector.runtime.jobworker.impl.feel.FeelEngineWrapper;
import io.camunda.connector.runtime.jobworker.impl.feel.FeelEngineWrapperException;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Job worker handler wrapper for a connector function. */
public class ConnectorJobHandler implements JobHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorJobHandler.class);

  private static final String ERROR_CANNOT_PARSE_VARIABLES = "Cannot parse variables";

  protected static final String RESULT_VARIABLE_HEADER_NAME = "resultVariable";
  protected static final String RESULT_EXPRESSION_HEADER_NAME = "resultExpression";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final OutboundConnectorFunction call;
  private final FeelEngineWrapper feelEngineWrapper;

  /**
   * Create a handler wrapper for the specified connector function.
   *
   * @param call - the connector function to call
   */
  public ConnectorJobHandler(
      final OutboundConnectorFunction call, final FeelEngineWrapper feelEngineWrapper) {
    this.call = call;
    this.feelEngineWrapper = feelEngineWrapper;
  }

  @Override
  public void handle(final JobClient client, final ActivatedJob job) {

    LOGGER.info("Received job {}", job.getKey());

    try {
      Object result = call.execute(new JobHandlerContext(job));

      client
          .newCompleteCommand(job)
          .variables(createOutputVariables(result, job.getCustomHeaders()))
          .send()
          .join();

      LOGGER.debug("Completed job {}", job.getKey());
    } catch (Exception error) {

      LOGGER.error("Failed to process job {}", job.getKey(), error);

      client.newFailCommand(job).retries(0).errorMessage(error.getMessage()).send().join();
    }
  }

  protected Map<String, Object> createOutputVariables(
      final Object responseContent, final Map<String, String> jobHeaders) {
    final Map<String, Object> outputVariables = new HashMap<>();
    final var resultVariableName = jobHeaders.get(RESULT_VARIABLE_HEADER_NAME);
    final var resultExpression = jobHeaders.get(RESULT_EXPRESSION_HEADER_NAME);

    if (resultVariableName != null) {
      outputVariables.put(resultVariableName, responseContent);
    }

    Optional.ofNullable(resultExpression)
        .map(expression -> feelEngineWrapper.evaluateToJson(expression, responseContent))
        .map(json -> parseJsonVarsAsMapOrThrow(json, resultExpression))
        .ifPresent(outputVariables::putAll);

    return outputVariables;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parseJsonVarsAsMapOrThrow(
      final String jsonVars, final String expression) {
    try {
      return OBJECT_MAPPER.readValue(jsonVars, Map.class);
    } catch (JsonProcessingException e) {
      throw new FeelEngineWrapperException(ERROR_CANNOT_PARSE_VARIABLES, expression, jsonVars, e);
    }
  }

  protected SecretProvider getSecretProvider() {
    return ServiceLoader.load(SecretProvider.class).findFirst().orElse(getEnvSecretProvider());
  }

  protected SecretProvider getEnvSecretProvider() {
    return System::getenv;
  }

  protected class JobHandlerContext extends AbstractOutboundConnectorContext {

    private final ActivatedJob job;
    private SecretStore secretStore;

    public JobHandlerContext(final ActivatedJob job) {
      this.job = job;
    }

    @Override
    public SecretStore getSecretStore() {
      if (secretStore == null) {
        secretStore = new SecretStore(ConnectorJobHandler.this.getSecretProvider());
      }
      return secretStore;
    }

    @Override
    public <T> T getVariablesAsType(Class<T> cls) {
      return job.getVariablesAsType(cls);
    }

    @Override
    public String getVariables() {
      return job.getVariables();
    }
  }
}
