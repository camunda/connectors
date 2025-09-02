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
package io.camunda.connector.test;

/**
 * Enumeration of external systems for system integration tests. The {@link #id} value is also used
 * as the expected environment variable name that must be present to enable tests annotated with
 * {@link SystemIntegrationTest}.
 */
public enum ExternalSystem {
  ServiceNow("ServiceNow");

  public final String id;

  ExternalSystem(String id) {
    this.id = id;
  }
}
