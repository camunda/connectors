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

import java.util.List;

/**
 * Represents a registered outbound connector on a specific runtime node.
 *
 * @param name connector name as declared in {@code @OutboundConnector}
 * @param type job type the worker subscribes to
 * @param inputVariables variables fetched from the job
 * @param timeout job timeout in milliseconds, or {@code null} if not configured
 * @param enabled whether the connector is enabled or not
 * @param runtimeId hostname of the runtime node that reported this entry
 */
public record OutboundConnectorResponse(
    String name,
    String type,
    List<String> inputVariables,
    Long timeout,
    boolean enabled,
    String runtimeId) {}
