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
package io.camunda.connector.runtime.outbound.controller;

import io.camunda.connector.runtime.core.outbound.configuration.ConfigurationValidationRequest;
import io.camunda.connector.runtime.core.outbound.configuration.ConfigurationValidationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Validates a stored configuration (credential) out-of-band by invoking the {@code
 * ConfigurationValidator} registered for the given {@code credentialId}.
 *
 * <p>{@code POST /outbound/configurations/validate} with {@code {credentialId, credentialRef,
 * tenantId}} returns one of {@code {"status":"SUCCESS"}}, {@code {"status":"FAILURE","code":...,
 * "message":...}}, or {@code {"status":"UNSUPPORTED"}}.
 */
@RestController
@RequestMapping("/outbound/configurations")
public class ConfigurationValidationRestController {

  private final ConfigurationValidationService configurationValidationService;

  public ConfigurationValidationRestController(
      ConfigurationValidationService configurationValidationService) {
    this.configurationValidationService = configurationValidationService;
  }

  @PostMapping("/validate")
  public ConfigurationValidationResponse validate(
      @RequestBody ConfigurationValidationRequest request) {
    return ConfigurationValidationResponse.from(configurationValidationService.validate(request));
  }
}
