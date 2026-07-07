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
package io.camunda.connector.runtime.core.outbound.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a stored configuration referenced by a validation request and invokes its {@link
 * ConfigurationValidator#validate()}, mapping the outcome to a {@link
 * ConfigurationValidationResult}.
 *
 * <p>Resolution pipeline: look up the configuration class by id, resolve {@code credentialRef} to
 * JSON via the (cluster-backed) FEEL evaluator, replace secret placeholders, deserialize into that
 * class, then call {@code validate()}. A missing registration yields {@code UNSUPPORTED}; a failed
 * resolution or a thrown exception yields {@code FAILURE}.
 */
public class ConfigurationValidationService {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigurationValidationService.class);

  private static final String DEFAULT_FAILURE_CODE = "ERROR";
  private static final String RESOLUTION_FAILURE_CODE = "RESOLUTION_ERROR";

  private final ConfigurationValidationRegistry registry;
  private final FeelExpressionEvaluator feelExpressionEvaluator;
  private final SecretHandler secretHandler;
  private final ObjectMapper objectMapper;

  public ConfigurationValidationService(
      ConfigurationValidationRegistry registry,
      FeelExpressionEvaluator feelExpressionEvaluator,
      SecretProvider secretProvider,
      ObjectMapper objectMapper) {
    this.registry = registry;
    this.feelExpressionEvaluator = feelExpressionEvaluator;
    // Out-of-band validation has no process/element scope, so no secret allow-list applies.
    this.secretHandler = new SecretHandler(secretProvider, SecretFilter.allowAll());
    this.objectMapper = objectMapper;
  }

  public ConfigurationValidationResult validate(ConfigurationValidationRequest request) {
    Class<? extends ConfigurationValidator> configurationClass =
        registry.findById(request.credentialId()).orElse(null);
    if (configurationClass == null) {
      return ConfigurationValidationResult.unsupported();
    }

    final ConfigurationValidator configuration;
    try {
      configuration = resolveConfiguration(request, configurationClass);
    } catch (Exception e) {
      LOG.warn(
          "Failed to resolve configuration '{}' from ref '{}'",
          request.credentialId(),
          request.credentialRef(),
          e);
      return ConfigurationValidationResult.failure(RESOLUTION_FAILURE_CODE, e.getMessage());
    }

    try {
      return configuration.validate();
    } catch (Exception e) {
      String code =
          e instanceof ConnectorException ce && ce.getErrorCode() != null
              ? ce.getErrorCode()
              : DEFAULT_FAILURE_CODE;
      return ConfigurationValidationResult.failure(code, e.getMessage());
    }
  }

  private ConfigurationValidator resolveConfiguration(
      ConfigurationValidationRequest request,
      Class<? extends ConfigurationValidator> configurationClass)
      throws Exception {
    String rawJson = feelExpressionEvaluator.evaluateToJson(request.credentialRef());
    String withSecrets =
        secretHandler.replaceSecrets(rawJson, new SecretContext(request.tenantId(), null));
    return objectMapper.readValue(withSecrets, configurationClass);
  }
}
