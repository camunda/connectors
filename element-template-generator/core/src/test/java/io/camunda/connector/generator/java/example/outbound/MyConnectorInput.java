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

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DefaultValueType;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.generator.java.example.outbound.MyConnectorInput.AnnotatedSealedType.FirstAnnotatedSubType;
import io.camunda.connector.generator.java.example.outbound.MyConnectorInput.AnnotatedSealedType.IgnoredSubType;
import io.camunda.connector.generator.java.example.outbound.MyConnectorInput.AnnotatedSealedType.NestedAnnotatedSealedType;
import io.camunda.connector.generator.java.example.outbound.MyConnectorInput.AnnotatedSealedType.NestedAnnotatedSealedType.NestedAnnotatedSubType;
import io.camunda.connector.generator.java.example.outbound.MyConnectorInput.AnnotatedSealedType.SecondAnnotatedSubType;
import io.camunda.connector.generator.java.example.outbound.MyConnectorInput.NonAnnotatedSealedType.FirstSubType;
import io.camunda.connector.generator.java.example.outbound.MyConnectorInput.NonAnnotatedSealedType.NestedSealedType;
import io.camunda.connector.generator.java.example.outbound.MyConnectorInput.NonAnnotatedSealedType.NestedSealedType.NestedSubType;
import io.camunda.connector.generator.java.example.outbound.MyConnectorInput.NonAnnotatedSealedType.SecondSubType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record MyConnectorInput(
    @TemplateProperty(
            id = "annotatedStringProperty",
            binding = @TemplateProperty.PropertyBinding(name = "customBinding"),
            label = "Annotated and renamed string property",
            type = PropertyType.Text,
            group = "group1",
            description = "description",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            tooltip = "tooltip")
        String annotatedStringProperty,
    String notAnnotatedStringProperty,
    Object objectProperty,
    JsonNode jsonNodeProperty,
    MyEnum enumProperty,
    LocalDate dateProperty,
    @TemplateProperty(type = PropertyType.String) NestedWithDefinedGroup propertyWithTypeOverride,
    NestedWithoutDefinedGroup nestedProperty,
    @NestedProperties(group = "customGroup") NestedWithoutDefinedGroup nestedPropertyWithGroup,
    @NestedProperties(addNestedPath = false) NestedWithDefinedGroup customPathNestedProperty,
    @NestedProperties(group = "customGroup") NestedWithDefinedGroup nestedPropertyWithGroupOverride,
    NonAnnotatedSealedType nonAnnotatedSealedType,
    AnnotatedSealedType annotatedSealedType,
    @NestedProperties(
            condition = @PropertyCondition(property = "annotatedStringProperty", equals = "value"))
        SealedTypeWithCondition sealedTypeWithCondition,
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
    @TemplateProperty(ignore = true) String ignoredField,
    @TemplateProperty(type = PropertyType.Text)
        @Pattern(regexp = "^(=.*|[0-9]+|\\{\\{secrets\\..+}})$", message = "Pattern violated")
        String propertyWithPattern,
    @TemplateProperty(id = "idNotEqualToBinding") String propertyWithDifferentIdAndBinding,
    @Size(min = 1, max = 10) String propertyWithMinMax,
    @Size(min = Integer.MIN_VALUE, max = 10) String propertyWithMaxSize,
    @NotEmpty String stringPropertyWithNotEmpty,
    @NotBlank String stringPropertyWithNotBlank,
    @NotNull Object objectPropertyWithNotNull,
    @TemplateProperty(
            id = "booleanProperty",
            defaultValue = "false",
            defaultValueType = DefaultValueType.Boolean)
        Boolean booleanProperty,
    @TemplateProperty(
            id = "dependsOnBooleanPropertyFalse",
            condition = @PropertyCondition(property = "booleanProperty", equals = "false"))
        String dependsOnBooleanPropertyFalse,
    @TemplateProperty(
            id = "dependsOnBooleanPropertyTrue",
            condition = @PropertyCondition(property = "booleanProperty", equals = "true"))
        String dependsOnBooleanPropertyTrue) {

  sealed interface NonAnnotatedSealedType permits FirstSubType, NestedSealedType, SecondSubType {

    record FirstSubType(String firstSubTypeValue) implements NonAnnotatedSealedType {}

    record SecondSubType(String secondSubTypeValue) implements NonAnnotatedSealedType {}

    sealed interface NestedSealedType extends NonAnnotatedSealedType permits NestedSubType {

      record NestedSubType(String thirdSubTypeValue) implements NestedSealedType {}
    }
  }

  @TemplateDiscriminatorProperty(
      name = "annotatedTypeOverride",
      label = "Annotated type override",
      id = "annotatedTypeOverrideCustomId")
  sealed interface AnnotatedSealedType
      permits FirstAnnotatedSubType,
          IgnoredSubType,
          NestedAnnotatedSealedType,
          SecondAnnotatedSubType {

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

    @TemplateSubType(
        id = "nestedAnnotatedSealedType",
        label = "Nested annotated sealed type override")
    @TemplateDiscriminatorProperty(
        name = "nestedSubTypeOverride",
        label = "Nested discriminator property")
    sealed interface NestedAnnotatedSealedType extends AnnotatedSealedType
        permits NestedAnnotatedSubType {

      @TemplateSubType(id = "firstNestedSubTypeOverride", label = "First nested sub type override")
      record NestedAnnotatedSubType(
          @TemplateProperty(
                  label = "First nested sub type override value",
                  condition =
                      @PropertyCondition(property = "annotatedStringProperty", equals = "value"))
              String firstNestedSubTypeValue)
          implements NestedAnnotatedSealedType {}
    }
  }

  @TemplateDiscriminatorProperty(
      name = "conditionalDiscriminator",
      label = "Conditional discriminator")
  sealed interface SealedTypeWithCondition {
    @TemplateSubType(id = "conditionalSubType", label = "Conditional sub type")
    record ConditionalSubType(
        @TemplateProperty(label = "Conditional sub type value") String conditionalSubTypeValue)
        implements SealedTypeWithCondition {}
  }

  enum MyEnum {
    VALUE1,
    VALUE2
  }

  record NestedWithoutDefinedGroup(@TemplateProperty(id = "nestedA") String a) {}

  record NestedWithDefinedGroup(@TemplateProperty(id = "nestedB") String b) {}
}
