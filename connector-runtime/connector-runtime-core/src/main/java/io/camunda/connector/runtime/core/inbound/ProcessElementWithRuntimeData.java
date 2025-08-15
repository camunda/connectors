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
package io.camunda.connector.runtime.core.inbound;

import io.camunda.connector.api.inbound.ElementTemplateDetails;
import io.camunda.connector.api.inbound.ProcessElement;
import java.util.Map;

/** Represents a BPMN process element that contains an inbound connector definition. */
public record ProcessElementWithRuntimeData(
    String bpmnProcessId,
    int version,
    long processDefinitionKey,
    String elementId,
    String elementName,
    String elementType,
    String tenantId,
    ElementTemplateDetails elementTemplateDetails,
    Map<String, String> properties)
    implements ProcessElement {

  public ProcessElementWithRuntimeData(
      String bpmnProcessId,
      int version,
      long processDefinitionKey,
      String elementId,
      String tenantId) {
    this(
        bpmnProcessId,
        version,
        processDefinitionKey,
        elementId,
        null,
        null,
        tenantId,
        new ElementTemplateDetails("Test", "1", "icon"),
        Map.of());
  }
}
