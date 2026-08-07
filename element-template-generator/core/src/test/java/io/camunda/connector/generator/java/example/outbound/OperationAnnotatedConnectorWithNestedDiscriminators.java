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
package io.camunda.connector.generator.java.example.outbound;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.api.annotation.Operation;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.annotation.Variable;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

/**
 * A sealed field nested inside a sealed subtype, on an {@code @Operation}-based connector. The
 * inner properties end up with an {@code AllMatch} of both discriminators, which the operation
 * mapping then has to merge the operation condition into — a combination that previously threw
 * {@code UnsupportedOperationException} because the merge mutated an immutable list.
 */
@OutboundConnector(
    name = OperationAnnotatedConnectorWithNestedDiscriminators.NAME,
    type = OperationAnnotatedConnectorWithNestedDiscriminators.TYPE)
@ElementTemplate(
    id = OperationAnnotatedConnectorWithNestedDiscriminators.ID,
    name = OperationAnnotatedConnectorWithNestedDiscriminators.NAME,
    engineVersion = "^8.8",
    propertyGroups = {@ElementTemplate.PropertyGroup(id = "main", label = "Main")})
public class OperationAnnotatedConnectorWithNestedDiscriminators
    implements OutboundConnectorProvider {

  public static final String ID = "op-annotated-with-nested-discriminators-id";
  public static final String TYPE = "op-annotated-with-nested-discriminators-type";
  public static final String NAME = "Operation Annotated Connector With Nested Discriminators";

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({@JsonSubTypes.Type(value = Inner.InnerA.class, name = "innerA")})
  @TemplateDiscriminatorProperty(name = "type", group = "main", label = "Inner")
  public sealed interface Inner permits Inner.InnerA {
    @TemplateSubType(id = "innerA", label = "Inner A")
    record InnerA(@TemplateProperty(group = "main", label = "Deep field") String deep)
        implements Inner {}
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({@JsonSubTypes.Type(value = Outer.OuterA.class, name = "outerA")})
  @TemplateDiscriminatorProperty(name = "type", group = "main", label = "Outer")
  public sealed interface Outer permits Outer.OuterA {
    @TemplateSubType(id = "outerA", label = "Outer A")
    record OuterA(Inner nested) implements Outer {}
  }

  record RequestWithNestedDiscriminators(Outer outer) {}

  @Operation(id = "op1", name = "Operation 1")
  @SuppressWarnings("unused")
  public String op1(@Variable RequestWithNestedDiscriminators request) {
    return null;
  }
}
