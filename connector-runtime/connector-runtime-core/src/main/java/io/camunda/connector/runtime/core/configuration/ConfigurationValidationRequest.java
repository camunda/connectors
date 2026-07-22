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

/**
 * Payload of the configuration validation endpoint.
 *
 * @param credentialId the configuration id to validate (matches {@code @Configuration#id})
 * @param credentialRef a FEEL expression pointing at the stored configuration (cluster variable),
 *     e.g. {@code =camunda.vars.env.awsProd}
 * @param tenantId the tenant the configuration belongs to; used for secret resolution
 */
public record ConfigurationValidationRequest(
    String credentialId, String credentialRef, String tenantId) {}
