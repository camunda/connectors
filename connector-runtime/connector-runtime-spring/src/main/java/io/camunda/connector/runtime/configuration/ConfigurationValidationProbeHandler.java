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
package io.camunda.connector.runtime.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.worker.probe.ActivatedProbe;
import io.camunda.client.api.worker.probe.ProbeAnswer;
import io.camunda.client.api.worker.probe.ProbeHandler;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.configuration.ConfigurationValidationService;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answers the validation probes of one configuration. The cluster has already evaluated the input
 * and injected its {@code camunda.secrets.*} values; this handler only replaces the legacy {@code
 * {{secrets.X}}} syntax from the runtime's own secret providers, as for jobs, then validates with
 * {@link ConfigurationValidationService}. Every result, including every failure, is answered as
 * {@code OK} with the result in {@code data}.
 *
 * <p>The legacy replacement allows every name: there is no process model to derive an allow-list
 * from, and the input is written by the caller that created the probe.
 */
public class ConfigurationValidationProbeHandler implements ProbeHandler {

  private static final Logger LOG =
      LoggerFactory.getLogger(ConfigurationValidationProbeHandler.class);

  private static final String SECRET_RESOLUTION_FAILURE_MESSAGE =
      "A secret of the configuration could not be resolved.";

  private static final ObjectMapper OBJECT_MAPPER = ConnectorsObjectMapperSupplier.getCopy();

  private final String configurationId;
  private final ConfigurationValidationService validationService;
  private final SecretProvider secretProvider;

  public ConfigurationValidationProbeHandler(
      String configurationId,
      ConfigurationValidationService validationService,
      SecretProvider secretProvider) {
    this.configurationId = configurationId;
    this.validationService = validationService;
    this.secretProvider = secretProvider;
  }

  @Override
  public ProbeAnswer handle(ActivatedProbe probe) {
    return ProbeAnswer.ok(ConfigurationValidationResponse.from(validate(probe)));
  }

  private ConfigurationValidationResult validate(ActivatedProbe probe) {
    final JsonNode input;
    try {
      input = replaceLegacySecrets(probe);
    } catch (Exception e) {
      // Log only the exception type, never the throwable: the input holds injected secret values.
      LOG.warn(
          "Failed to replace the legacy secrets of configuration '{}' ({})",
          configurationId,
          e.getClass().getName());
      return ConfigurationValidationResult.failure(
          ErrorCode.RESOLUTION_ERROR, SECRET_RESOLUTION_FAILURE_MESSAGE);
    }
    return validationService.validate(configurationId, input);
  }

  private JsonNode replaceLegacySecrets(ActivatedProbe probe) throws Exception {
    JsonNode input = OBJECT_MAPPER.readTree(probe.getInput());
    if (!input.isObject()) {
      return input;
    }
    return new SecretHandler(secretProvider, SecretFilter.allowAll())
        .replaceSecrets(
            input, new SecretContext(probe.getTenantId(), null, probe.getPhysicalTenantId()));
  }
}
