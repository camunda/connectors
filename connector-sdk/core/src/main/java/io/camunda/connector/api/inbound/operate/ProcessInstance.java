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
package io.camunda.connector.api.inbound.operate;

import java.util.Map;

/**
 * Represents a process instance with associated variables.
 *
 * <p>A process instance is a single execution of a process definition within a workflow engine. The
 * associated variables contain the dynamic data of the process instance at a given point in time.
 * These variables can change during the lifetime of the process instance, reflecting the evolving
 * state of the instance.
 *
 * @param key The unique identifier of the process instance.
 * @param variables A map containing the variables associated with this process instance. The keys
 *     represent variable names, and the values are the variable values.
 */
public record ProcessInstance(Long key, Map<String, String> variables) {}
