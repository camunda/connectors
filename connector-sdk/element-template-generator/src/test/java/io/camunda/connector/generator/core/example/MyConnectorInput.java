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
package io.camunda.connector.generator.core.example;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.generator.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.annotation.TemplateProperty;
import io.camunda.connector.generator.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.annotation.TemplateSubType;
import io.camunda.connector.generator.core.example.MyConnectorInput.AnnotatedSealedType.FirstAnnotatedSubType;
import io.camunda.connector.generator.core.example.MyConnectorInput.AnnotatedSealedType.IgnoredSubType;
import io.camunda.connector.generator.core.example.MyConnectorInput.AnnotatedSealedType.SecondAnnotatedSubType;
import io.camunda.connector.generator.core.example.MyConnectorInput.NonAnnotatedSealedType.FirstSubType;
import io.camunda.connector.generator.core.example.MyConnectorInput.NonAnnotatedSealedType.SecondSubType;

public record MyConnectorInput(
    @TemplateProperty(
            id = "annotatedStringProperty",
            label = "Annotated and renamed string property",
            type = PropertyType.Text,
            group = "group1",
            description = "description")
        String annotatedStringProperty,
    String notAnnotatedStringProperty,
    Object objectProperty,
    JsonNode jsonNodeProperty,
    MyEnum enumProperty,
    NestedA nestedProperty,
    @TemplateProperty(addNestedPath = false) NestedB customPathNestedProperty,
    NonAnnotatedSealedType nonAnnotatedSealedType,
    AnnotatedSealedType annotatedSealedType,
    @TemplateProperty(
            condition = @PropertyCondition(property = "annotatedStringProperty", equals = "value"))
        String conditionalPropertyEquals,
    @TemplateProperty(
            condition =
                @PropertyCondition(
                    property = "annotatedStringProperty",
                    oneOf = {"value1", "value2"}))
        String conditionalPropertyOneOf,
    @TemplateProperty(group = "group1") String propertyForGroup1,
    @TemplateProperty(group = "group2") String propertyForGroup2,
    @TemplateProperty(ignore = true) String ignoredField) {

  sealed interface NonAnnotatedSealedType permits FirstSubType, SecondSubType {

    record FirstSubType(String firstSubTypeValue) implements NonAnnotatedSealedType {}

    record SecondSubType(String secondSubTypeValue) implements NonAnnotatedSealedType {}
  }

  @TemplateDiscriminatorProperty(name = "annotatedTypeOverride", label = "Annotated type override")
  sealed interface AnnotatedSealedType
      permits IgnoredSubType, FirstAnnotatedSubType, SecondAnnotatedSubType {

    @TemplateSubType(id = "firstAnnotatedOverride", label = "First annotated override")
    record FirstAnnotatedSubType(
        @TemplateProperty(label = "First annotated override value") String firstAnnotatedValue)
        implements AnnotatedSealedType {}

    @TemplateSubType(id = "secondAnnotatedOverride", label = "Second annotated override")
    record SecondAnnotatedSubType(
        @TemplateProperty(label = "Second annotated override value") String secondAnnotatedValue)
        implements AnnotatedSealedType {}

    @TemplateSubType(ignore = true)
    record IgnoredSubType() implements AnnotatedSealedType {}
  }

  enum MyEnum {
    VALUE1,
    VALUE2
  }

  record NestedA(@TemplateProperty(id = "nestedA") String a) {}

  record NestedB(@TemplateProperty(id = "nestedB") String b) {}
}
