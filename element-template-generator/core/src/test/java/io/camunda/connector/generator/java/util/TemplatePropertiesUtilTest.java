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
package io.camunda.connector.generator.java.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.java.util.TemplateGenerationContext.Inbound;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class TemplatePropertiesUtilTest {

  private static final Inbound INBOUND_CONTEXT = new Inbound("my-type", Set.of());

  @ParameterizedTest
  @CsvSource({
    "myProperty, My property",
    "myPropertyWithCamelCase, My property with camel case",
    "myPropertyWithCamelCaseAndNumbers123, My property with camel case and numbers 123",
    "MY_UPPERCASE_PROPERTY,MY_UPPERCASE_PROPERTY"
  })
  void transformIntoLabel(String input, String expected) {
    // when
    var actual = TemplatePropertiesUtil.transformIdIntoLabel(input);

    // then
    assertThat(actual).isEqualTo(expected);
  }

  /** A model with a bound instance field, a template-only static field, and a plain constant. */
  record ModelWithStaticTemplateProperty(
      @TemplateProperty(id = "instanceProp") String instanceProp) {

    @TemplateProperty(
        id = "staticProp",
        label = "Static prop",
        type = PropertyType.Text,
        group = "responses")
    private static final String staticProp = null;

    private static final String NOT_A_TEMPLATE_PROPERTY = "constant";
  }

  @Test
  void staticTemplateProperty_isEmitted() {
    var properties =
        TemplatePropertiesUtil.extractTemplatePropertiesFromType(
            ModelWithStaticTemplateProperty.class, INBOUND_CONTEXT);

    var staticProp =
        properties.stream()
            .map(builder -> builder.build())
            .filter(p -> "staticProp".equals(p.getId()))
            .findFirst()
            .orElseThrow();

    assertThat(staticProp.getType()).isEqualTo("Text");
    assertThat(staticProp.getGroup()).isEqualTo("responses");
    assertThat(staticProp.getBinding()).isEqualTo(new ZeebeProperty("staticProp"));
  }

  @Test
  void nonAnnotatedStaticField_isNotEmitted() {
    var ids =
        TemplatePropertiesUtil.extractTemplatePropertiesFromType(
                ModelWithStaticTemplateProperty.class, INBOUND_CONTEXT)
            .stream()
            .map(p -> p.build().getId())
            .toList();

    assertThat(ids)
        .contains("instanceProp", "staticProp")
        .doesNotContain("NOT_A_TEMPLATE_PROPERTY");
  }
}
