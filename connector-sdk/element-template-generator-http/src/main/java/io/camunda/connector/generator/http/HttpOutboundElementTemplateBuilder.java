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
package io.camunda.connector.generator.http;

import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.ElementTemplateIcon;
import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.OutboundElementTemplate;
import io.camunda.connector.generator.dsl.OutboundElementTemplateBuilder;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.dsl.PropertyGroup;
import io.camunda.connector.http.base.auth.Authentication;
import io.camunda.connector.http.base.auth.NoAuthentication;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class HttpOutboundElementTemplateBuilder {

  private static final String CONNECTOR_TYPE = "io.camunda:http-json:1";

  private final OutboundElementTemplateBuilder builder;

  private Collection<HttpServerData> servers;
  private Collection<HttpOperation> operations;
  private Authentication authentication;

  private HttpOutboundElementTemplateBuilder() {
    builder = OutboundElementTemplateBuilder.create().type(CONNECTOR_TYPE);
  }

  public static HttpOutboundElementTemplateBuilder create() {
    return new HttpOutboundElementTemplateBuilder();
  }

  public HttpOutboundElementTemplateBuilder id(String id) {
    builder.id(id);
    return this;
  }

  public HttpOutboundElementTemplateBuilder name(String name) {
    builder.name(name);
    return this;
  }

  public HttpOutboundElementTemplateBuilder version(int version) {
    builder.version(version);
    return this;
  }

  public HttpOutboundElementTemplateBuilder icon(ElementTemplateIcon icon) {
    builder.icon(icon);
    return this;
  }

  public HttpOutboundElementTemplateBuilder documentationRef(String documentationRef) {
    builder.documentationRef(documentationRef);
    return this;
  }

  public HttpOutboundElementTemplateBuilder description(String description) {
    builder.description(description);
    return this;
  }

  public HttpOutboundElementTemplateBuilder servers(Collection<HttpServerData> servers) {
    this.servers = new ArrayList<>(servers);
    return this;
  }

  public HttpOutboundElementTemplateBuilder servers(HttpServerData... servers) {
    return servers(Arrays.asList(servers));
  }

  public HttpOutboundElementTemplateBuilder operations(Collection<HttpOperation> operations) {
    if (this.operations != null && !this.operations.isEmpty()) {
      throw new IllegalStateException("Operations are already set: " + operations);
    }
    this.operations = new ArrayList<>(operations);
    return this;
  }

  public HttpOutboundElementTemplateBuilder operations(HttpOperation... operations) {
    return operations(Arrays.asList(operations));
  }

  public HttpOutboundElementTemplateBuilder operation(HttpOperation operation) {
    if (operations == null) {
      operations = new ArrayList<>();
    }
    operations.add(operation);
    return this;
  }

  public HttpOutboundElementTemplateBuilder authentication(Authentication authentication) {
    this.authentication = authentication;
    return this;
  }

  public OutboundElementTemplate build() {
    if (servers == null || servers.isEmpty()) {
      throw new IllegalStateException("No servers configured");
    }
    if (operations == null || operations.isEmpty()) {
      throw new IllegalStateException("No operations configured");
    }
    if (authentication == null) {
      authentication = new NoAuthentication();
    }
    builder.propertyGroups(List.of(serverPropertyGroup(), operationPropertyGroup()));
    builder.properties(businessLogicPropertyGroups());
    return builder.build();
  }

  private PropertyGroup serverPropertyGroup() {
    if (servers.size() == 1) {
      return PropertyGroup.builder()
          .id("server")
          .label("Server")
          .properties(
              HiddenProperty.builder().id("baseUrl").value(servers.iterator().next().baseUrl()))
          .build();
    }

    return PropertyGroup.builder()
        .id("server")
        .label("Server")
        .properties(
            DropdownProperty.builder()
                .choices(
                    servers.stream()
                        .map(server -> new DropdownChoice(server.label(), server.baseUrl()))
                        .collect(Collectors.toList()))
                .id("baseUrl")
                .label("Server")
                .group("server")
                .build())
        .build();
  }

  private PropertyGroup operationPropertyGroup() {
    if (operations.size() == 1) {
      return PropertyGroup.builder()
          .id("operation")
          .label("Operation")
          .properties(
              HiddenProperty.builder().id("operationId").value(operations.iterator().next().id()))
          .build();
    }

    return PropertyGroup.builder()
        .id("operation")
        .label("Operation")
        .properties(
            DropdownProperty.builder()
                .choices(
                    operations.stream()
                        .map(operation -> new DropdownChoice(operation.label(), operation.id()))
                        .collect(Collectors.toList()))
                .id("operationId")
                .label("Operation")
                .group("operation")
                .build())
        .build();
  }

  private List<Property> businessLogicPropertyGroups() {
    var preparedProperties = new ArrayList<Property>();

    for (var operation : operations) {
      for (var property : operation.properties()) {
        preparedProperties.add(
            property
                .condition(new PropertyCondition.Equals("operationId", operation.id()))
                .build());
      }
    }
    return preparedProperties;
  }
}
