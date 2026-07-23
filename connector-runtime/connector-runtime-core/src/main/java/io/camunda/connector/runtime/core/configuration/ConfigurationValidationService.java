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
package io.camunda.connector.runtime.core.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.runtime.core.configuration.ConfigurationValidationRegistry.RegisteredValidator;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a stored configuration referenced by a validation request, applies bean validation, and
 * invokes its {@link ConfigurationValidator}, mapping the outcome to a {@link
 * ConfigurationValidationResult}.
 *
 * <p>Pipeline: look up the registered validator by id; resolve {@code credentialRef} to JSON via
 * the (cluster-backed) FEEL evaluator; replace secret placeholders; deserialize into the registered
 * configuration class; run Jakarta bean validation (as normal connector binding does); then call
 * the validator. A missing registration yields {@code UNSUPPORTED}; anything else that goes wrong
 * yields {@code FAILURE}.
 *
 * <p><b>Message-safety policy.</b> The resolved configuration and everything derived from it
 * (deserialization errors, constraint-violation messages, exceptions thrown from connector code)
 * can contain resolved <em>secret material</em>. Surfacing any of it would turn this endpoint into
 * a secret oracle. Therefore the client-facing {@code message} is populated <b>only</b> from a
 * validator's explicitly returned {@link ConfigurationValidationResult#failure(String, String)} —
 * text the connector author deliberately chose. Every failure this service generates itself returns
 * a static, value-free message; the real detail is logged server-side only.
 */
public class ConfigurationValidationService {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigurationValidationService.class);

  private static final String DEFAULT_FAILURE_CODE = "ERROR";
  private static final String RESOLUTION_FAILURE_CODE = "RESOLUTION_ERROR";
  private static final String INPUT_VALIDATION_FAILURE_CODE = "INVALID_INPUT";

  // Static, value-free messages. Never interpolate resolved configuration content into these.
  private static final String RESOLUTION_FAILURE_MESSAGE =
      "The configuration reference could not be resolved.";
  private static final String INPUT_VALIDATION_FAILURE_MESSAGE =
      "The resolved configuration is not valid.";
  private static final String VALIDATOR_ERROR_MESSAGE = "Validation could not be completed.";

  private final ConfigurationValidationRegistry registry;
  private final FeelExpressionEvaluator feelExpressionEvaluator;
  private final SecretHandler secretHandler;
  private final ValidationProvider validationProvider;
  private final ObjectMapper objectMapper;

  public ConfigurationValidationService(
      ConfigurationValidationRegistry registry,
      FeelExpressionEvaluator feelExpressionEvaluator,
      SecretProvider secretProvider,
      ValidationProvider validationProvider,
      ObjectMapper objectMapper) {
    this.registry = registry;
    this.feelExpressionEvaluator = feelExpressionEvaluator;
    // Out-of-band validation has no process/element scope, so no secret allow-list applies.
    this.secretHandler = new SecretHandler(secretProvider, SecretFilter.allowAll());
    this.validationProvider = validationProvider;
    this.objectMapper = objectMapper;
  }

  public ConfigurationValidationResult validate(ConfigurationValidationRequest request) {
    RegisteredValidator registered = registry.findById(request.credentialId()).orElse(null);
    if (registered == null) {
      return ConfigurationValidationResult.unsupported();
    }

    final Object configuration;
    try {
      configuration = resolveConfiguration(request, registered.configurationClass());
    } catch (Exception e) {
      // Log only the exception type, never the throwable: FEEL/secret/JSON error messages (and
      // stack-trace detail) can echo resolved secret material into the logs.
      LOG.warn(
          "Failed to resolve configuration '{}' from its reference ({})",
          request.credentialId(),
          e.getClass().getName());
      return ConfigurationValidationResult.failure(
          RESOLUTION_FAILURE_CODE, RESOLUTION_FAILURE_MESSAGE);
    }

    try {
      // Same object graph and constraints the normal binding path validates (JobHandlerContext).
      validationProvider.validate(configuration);
    } catch (Exception e) {
      // Log only the exception type, never the throwable: constraint-violation messages can
      // interpolate the invalid (secret) value.
      LOG.warn(
          "Resolved configuration '{}' failed input validation ({})",
          request.credentialId(),
          e.getClass().getName());
      return ConfigurationValidationResult.failure(
          INPUT_VALIDATION_FAILURE_CODE, INPUT_VALIDATION_FAILURE_MESSAGE);
    }

    try {
      @SuppressWarnings("unchecked")
      ConfigurationValidator<Object> validator =
          (ConfigurationValidator<Object>) registered.validator();
      // A validator's returned result (including its message) is passed through unchanged: that
      // text is author-authored and deliberate. A *thrown* exception is not — it may wrap
      // SDK/framework messages carrying credential material — so its message is logged, never
      // surfaced.
      ConfigurationValidationResult result = validator.validate(configuration);
      if (result == null) {
        // The SDK contract does not enforce a non-null result; a validator returning null would
        // otherwise NPE in response mapping and surface as HTTP 500. Normalize it to the same
        // value-free failure used for a thrown validator error.
        LOG.warn("Validator for configuration '{}' returned null", request.credentialId());
        return ConfigurationValidationResult.failure(DEFAULT_FAILURE_CODE, VALIDATOR_ERROR_MESSAGE);
      }
      return result;
    } catch (Exception e) {
      // Log only the exception type, never the throwable: a validator exception can wrap
      // SDK/framework messages carrying credential material.
      LOG.warn(
          "Validator for configuration '{}' threw ({})",
          request.credentialId(),
          e.getClass().getName());
      String code =
          e instanceof ConnectorException ce && ce.getErrorCode() != null
              ? ce.getErrorCode()
              : DEFAULT_FAILURE_CODE;
      return ConfigurationValidationResult.failure(code, VALIDATOR_ERROR_MESSAGE);
    }
  }

  private Object resolveConfiguration(
      ConfigurationValidationRequest request, Class<?> configurationClass) throws Exception {
    String rawJson = feelExpressionEvaluator.evaluateToJson(request.credentialRef());
    String withSecrets =
        secretHandler.replaceSecrets(rawJson, new SecretContext(request.tenantId(), null));
    return objectMapper.readValue(withSecrets, configurationClass);
  }
}
