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
package io.camunda.connector.http.rest.model;

import io.camunda.connector.generator.java.annotation.ConfigurationTemplate;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.http.base.model.auth.Authentication;

/**
 * Configuration (credential) template for reusable REST authentication. Reuses the existing sealed
 * {@link Authentication} union (none / basic / bearer / API key / OAuth / OAuth refresh token),
 * demonstrating that a rich multi-variant auth model maps onto the configuration-template format.
 */
@ConfigurationTemplate(
    id = "io.camunda.connectors:rest-authentication:1",
    version = 1,
    name = "REST Authentication")
public record RestAuthenticationConfiguration(
    @TemplateProperty(group = "authentication") Authentication authentication) {}
