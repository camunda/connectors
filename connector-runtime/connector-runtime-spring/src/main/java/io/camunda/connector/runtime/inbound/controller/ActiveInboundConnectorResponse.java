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
package io.camunda.connector.runtime.inbound.controller;

import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.inbound.executable.ActiveExecutableResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public record ActiveInboundConnectorResponse(
    UUID executableId, // consider
    String type,
    String tenantId,
    List<ProcessElement> elements,
    Map<String, String> data,
    Health health,
    Long activationTimestamp) {

  public static ActiveInboundConnectorResponse from(
      ActiveExecutableResponse connector,
      Function<ActiveExecutableResponse, Map<String, String>> dataMapper) {
    var elements = connector.elements();
    var type = elements.getFirst().type();
    var tenantId = elements.getFirst().element().tenantId();
    return new ActiveInboundConnectorResponse(
        connector.executableId(),
        type,
        tenantId,
        elements.stream().map(InboundConnectorElement::element).toList(),
        dataMapper.apply(connector),
        connector.health(),
        connector.activationTimestamp());
  }
}
