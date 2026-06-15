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
package io.camunda.connector.api.inbound;

import java.util.Map;

public interface ProcessElement {

  String bpmnProcessId();

  default String processName() {
    return null;
  }

  int version();

  long processDefinitionKey();

  String elementId();

  String elementName();

  String elementType();

  String tenantId();

  /**
   * Raw properties of this element as defined in the process model. FEEL expressions are not
   * evaluated and secret placeholders are not resolved.
   *
   * <p>This allows the runtime to resolve element-scoped properties (for example, a webhook
   * response expression) from the specific activated element even when several elements were
   * deduplicated into a single connector executable.
   */
  default Map<String, String> properties() {
    return Map.of();
  }
}
