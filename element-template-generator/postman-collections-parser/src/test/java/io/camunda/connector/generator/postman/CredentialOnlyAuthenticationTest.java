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
package io.camunda.connector.generator.postman;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorMode;
import io.camunda.connector.generator.api.GeneratorConfiguration.GenerationFeature;
import io.camunda.connector.generator.dsl.ConfigurationProperty;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Thin presence tests for the credential-only authentication chooser (#8113) and its {@code
 * LEGACY_INLINE_AUTHENTICATION} opt-out, for the Postman Collections generator. Deep behavioral
 * coverage (tooltip content, optionality rules, equivalence with the annotation-driven path) lives
 * in {@code HttpOutboundElementTemplateBuilderTest} in the http-dsl module; these tests only prove
 * the flag is threaded correctly end to end from a real collection.
 */
class CredentialOnlyAuthenticationTest {

  @Test
  void collectionWithBearerAuth_default_credentialOnlyChooserPresentAndRequired() {
    // given: operate-api-saas-bearer.json declares a top-level auth.type = bearer
    var source =
        new PostmanCollectionsGenerationSource(
            List.of("src/test/resources/operate-api-saas-bearer.json"));
    var generator = new PostmanCollectionOutboundTemplateGenerator();

    // when
    var template = generator.generate(source, GeneratorConfiguration.DEFAULT).getFirst();

    // then
    var chooser =
        template.properties().stream()
            .filter(p -> "authenticationConfiguration".equals(p.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(chooser).isInstanceOf(ConfigurationProperty.class);
    assertThat(((ConfigurationProperty) chooser).getConfigurationTemplate())
        .isEqualTo("io.camunda.connectors:rest-authentication:1");
    assertThat(chooser.getOptional()).isFalse();
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
  void collectionWithBearerAuth_legacyFlag_inlineAuthenticationRestored() {
    // given
    var source =
        new PostmanCollectionsGenerationSource(
            List.of("src/test/resources/operate-api-saas-bearer.json"));
    var generator = new PostmanCollectionOutboundTemplateGenerator();
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

    // then
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
  void collectionWithNoAuth_default_chooserPresentAndOptional() {
    // given: postman-books.json has no top-level auth block
    var source =
        new PostmanCollectionsGenerationSource(
            List.of(
                "src/test/resources/postman-books.json",
                "/1. Sending requests & inspecting responses/books"));
    var generator = new PostmanCollectionOutboundTemplateGenerator();

    // when
    var template = generator.generate(source, GeneratorConfiguration.DEFAULT).getFirst();

    // then
    var chooser =
        template.properties().stream()
            .filter(p -> "authenticationConfiguration".equals(p.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(chooser.getOptional()).isTrue();
    assertThat(template.configurationTemplates()).hasSize(1);
  }
}
