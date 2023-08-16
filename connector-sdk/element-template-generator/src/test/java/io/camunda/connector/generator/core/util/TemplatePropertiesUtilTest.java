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
package io.camunda.connector.generator.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.generator.core.example.MyConnectorInput;
import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.dsl.TextProperty;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class TemplatePropertiesUtilTest {

  @ParameterizedTest
  @CsvSource({
    "myProperty, My property",
    "myPropertyWithCamelCase, My property with camel case",
    "myPropertyWithCamelCaseAndNumbers123, My property with camel case and numbers 123"
  })
  void transformIntoLabel(String input, String expected) {
    // when
    var actual = TemplatePropertiesUtil.transformIdIntoLabel(input);

    // then
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void extractPropertiesFromType() {
    // given
    var type = MyConnectorInput.class;

    // when
    var actual = TemplatePropertiesUtil.extractTemplatePropertiesFromType(type);

    // then
    var props = actual.stream().map(PropertyBuilder::build).toList();
    assertThat(props)
        .containsExactlyInAnyOrder(
            TextProperty.builder()
                .id("message")
                .label("Message")
                .group("message")
                .optional(false)
                .build(),
            DropdownProperty.builder()
                .choices(
                    List.of(
                        new DropdownProperty.DropdownChoice("Basic", "basic"),
                        new DropdownProperty.DropdownChoice("Token", "token")))
                .id("authorization.authType")
                .label("Auth type")
                .group("settings")
                .optional(false)
                .build(),
            StringProperty.builder()
                .id("authorization.username")
                .label("Username")
                .group("auth")
                .feel(FeelMode.optional)
                .optional(false)
                .build(),
            StringProperty.builder()
                .id("authorization.password")
                .label("Password")
                .group("auth")
                .feel(FeelMode.optional)
                .optional(false)
                .build(),
            StringProperty.builder()
                .id("authorization.token")
                .label("Token")
                .group("auth")
                .optional(false)
                .build(),
            StringProperty.builder().id("recipient").label("Recipient").build());
  }
}
