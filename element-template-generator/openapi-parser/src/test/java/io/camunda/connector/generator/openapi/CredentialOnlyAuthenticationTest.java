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
package io.camunda.connector.generator.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorMode;
import io.camunda.connector.generator.api.GeneratorConfiguration.GenerationFeature;
import io.camunda.connector.generator.dsl.ConfigurationProperty;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.openapi.OpenApiGenerationSource.Options;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Thin presence tests for the credential-only authentication chooser (#8113) and its {@code
 * LEGACY_INLINE_AUTHENTICATION} opt-out, for the OpenAPI generator. Deep behavioral coverage
 * (tooltip content, optionality rules, equivalence with the annotation-driven path) lives in {@code
 * HttpOutboundElementTemplateBuilderTest} in the http-dsl module; these tests only prove the flag
 * is threaded correctly end to end from a real spec.
 */
class CredentialOnlyAuthenticationTest {

  private final OpenAPIV3Parser parser = new OpenAPIV3Parser();

  @Test
  void specWithSecurityScheme_default_credentialOnlyChooserPresent() {
    // given: every operation in this spec declares `security: [{Bearer: []}]`
    var openApi = parser.read("web-modeler-rest-api.json");
    var source = new OpenApiGenerationSource(openApi, Set.of(), new Options(false));
    var generator = new OpenApiOutboundTemplateGenerator();

    // when
    var template = generator.generate(source, GeneratorConfiguration.DEFAULT).getFirst();

    // then: exactly one Configuration chooser, no inline authentication.* properties
    var chooser =
        template.properties().stream()
            .filter(p -> "authenticationConfiguration".equals(p.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(chooser).isInstanceOf(ConfigurationProperty.class);
    assertThat(((ConfigurationProperty) chooser).getConfigurationTemplate())
        .isEqualTo("io.camunda.connectors:rest-authentication:1");
    assertThat(
            template.properties().stream()
                .filter(
                    p ->
                        p.getBinding() instanceof ZeebeInput zeebeInput
                            && zeebeInput.name().startsWith("authentication.")))
        .isEmpty();
    assertThat(template.configurationTemplates()).hasSize(1);
  }

  @Test
  void specWithSecurityScheme_legacyFlag_inlineAuthenticationRestored() {
    // given
    var openApi = parser.read("web-modeler-rest-api.json");
    var source = new OpenApiGenerationSource(openApi, Set.of(), new Options(false));
    var generator = new OpenApiOutboundTemplateGenerator();
    var configuration =
        new GeneratorConfiguration(
            ConnectorMode.NORMAL,
            null,
            null,
            null,
            null,
            Map.of(GenerationFeature.LEGACY_INLINE_AUTHENTICATION, true));

    // when
    var template = generator.generate(source, configuration).getFirst();

    // then: no Configuration chooser, no embedded configuration template, legacy inline
    // authentication.type property restored
    assertThat(template.properties())
        .noneMatch(p -> "authenticationConfiguration".equals(p.getId()));
    assertThat(template.configurationTemplates()).isEmpty();
    assertThat(
            template.properties().stream()
                .anyMatch(
                    p ->
                        p.getBinding() instanceof ZeebeInput zeebeInput
                            && zeebeInput.name().equals("authentication.type")))
        .isTrue();
  }

  @Test
  void specWithNoSecurityScheme_default_chooserStillPresentAndOptional() throws IOException {
    // given: no security requirement anywhere in the spec
    try (var openApiYamlContent = new FileInputStream("src/test/resources/example.yaml")) {
      byte[] raw = openApiYamlContent.readAllBytes();
      var source = new OpenApiGenerationSource(List.of(new String(raw)));
      var generator = new OpenApiOutboundTemplateGenerator();

      // when
      var template = generator.generate(source, GeneratorConfiguration.DEFAULT).getFirst();

      // then: the chooser is still emitted (a spec with no auth requirement maps to NoAuth,
      // which keeps the chooser optional rather than absent)
      var chooser =
          template.properties().stream()
              .filter(p -> "authenticationConfiguration".equals(p.getId()))
              .findFirst()
              .orElseThrow();
      assertThat(chooser.getOptional()).isTrue();
      assertThat(template.configurationTemplates()).hasSize(1);
    }
  }
}
