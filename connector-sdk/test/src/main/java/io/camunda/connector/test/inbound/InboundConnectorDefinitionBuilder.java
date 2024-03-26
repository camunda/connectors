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
package io.camunda.connector.test.inbound;

import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import io.camunda.connector.api.inbound.InboundConnectorElement;
import java.util.List;

/** Test helper class for creating an {@link InboundConnectorDefinition} with a fluent API. */
public class InboundConnectorDefinitionBuilder {

  private String type = "test-connector";

  private String tenantId = "test-tenant";

  private String deduplicationId = "test-deduplication-id";

  private List<InboundConnectorElement> elements =
      List.of(new InboundConnectorElementImpl("test-process", 1, 1L, "test-element"));

  public static InboundConnectorDefinitionBuilder create() {
    return new InboundConnectorDefinitionBuilder();
  }

  public InboundConnectorDefinitionBuilder tenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public InboundConnectorDefinitionBuilder type(String type) {
    this.type = type;
    return this;
  }

  public InboundConnectorDefinitionBuilder deduplicationId(String deduplicationId) {
    this.deduplicationId = deduplicationId;
    return this;
  }

  public InboundConnectorDefinitionBuilder elements(List<InboundConnectorElement> elements) {
    this.elements = elements;
    return this;
  }

  public InboundConnectorDefinitionBuilder elements(InboundConnectorElement... elements) {
    this.elements = List.of(elements);
    return this;
  }

  public InboundConnectorDefinition build() {
    return new InboundConnectorDefinitionImpl(type, tenantId, deduplicationId, elements);
  }

  public record InboundConnectorDefinitionImpl(
      String type, String tenantId, String deduplicationId, List<InboundConnectorElement> elements)
      implements InboundConnectorDefinition {}

  public record InboundConnectorElementImpl(
      String bpmnProcessId, int version, long processDefinitionKey, String elementId)
      implements InboundConnectorElement {}
}
