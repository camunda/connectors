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
package io.camunda.connector.generator.dsl.http;

import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorElementType;
import io.camunda.connector.generator.dsl.ElementTemplateIcon;
import io.camunda.connector.generator.dsl.OutboundElementTemplate;
import io.camunda.connector.generator.dsl.OutboundElementTemplateBuilder;
import io.camunda.connector.generator.dsl.PropertyGroup;
import io.camunda.connector.generator.dsl.http.HttpAuthentication.NoAuth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class HttpOutboundElementTemplateBuilder {

  private static final String CONNECTOR_TYPE = "io.camunda:http-json:1";

  private final OutboundElementTemplateBuilder builder;

  private Collection<HttpServerData> servers;
  private Collection<HttpOperation> operations;
  private List<HttpAuthentication> authentication = List.of(NoAuth.INSTANCE);

  private HttpOutboundElementTemplateBuilder(boolean configurable) {
    builder =
        OutboundElementTemplateBuilder.create()
            .type(CONNECTOR_TYPE, configurable)
            .icon(ElementTemplateIcon.from("rest-connector-icon.svg", getClass().getClassLoader()));
  }

  public static HttpOutboundElementTemplateBuilder create() {
    return new HttpOutboundElementTemplateBuilder(false);
  }

  public static HttpOutboundElementTemplateBuilder create(boolean configurable) {
    return new HttpOutboundElementTemplateBuilder(configurable);
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

  public HttpOutboundElementTemplateBuilder authentication(
      List<HttpAuthentication> authentication) {
    this.authentication = authentication;

    return this;
  }

  public HttpOutboundElementTemplateBuilder elementType(ConnectorElementType elementType) {
    builder.elementType(elementType.elementType());
    builder.appliesTo(elementType.appliesTo());
    return this;
  }

  public OutboundElementTemplate build() {
    if (operations == null || operations.isEmpty()) {
      throw new IllegalStateException("Could not find any supported operations");
    }
    return builder
        .propertyGroups(
            List.of(
                // Property order is important, parameters must come before their targets (URL,
                // headers, or body)
                // otherwise they will not be resolved correctly by the FEEL engine in Zeebe
                PropertyUtil.serverDiscriminatorPropertyGroup(servers),
                PropertyUtil.operationDiscriminatorPropertyGroup(operations),
                PropertyUtil.authPropertyGroup(authentication, operations),
                PropertyUtil.parametersPropertyGroup(operations),
                PropertyUtil.requestBodyPropertyGroup(operations),
                PropertyUtil.urlPropertyGroup(),
                PropertyGroup.OUTPUT_GROUP,
                PropertyGroup.ERROR_GROUP,
                PropertyGroup.RETRIES_GROUP))
        .build();
  }
}
