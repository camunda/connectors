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

import static io.camunda.connector.generator.dsl.http.PropertyUtil.authPropertyGroup;
import static io.camunda.connector.generator.dsl.http.PropertyUtil.parametersPropertyGroup;
import static io.camunda.connector.generator.dsl.http.PropertyUtil.serverDiscriminatorPropertyGroup;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorElementType;
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.dsl.PropertyGroup;
import io.camunda.connector.generator.dsl.http.HttpAuthentication.BearerAuth;
import io.camunda.connector.generator.dsl.http.HttpAuthentication.NoAuth;
import io.camunda.connector.generator.dsl.http.HttpAuthentication.OAuth2;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty.Target;
import io.camunda.connector.generator.java.annotation.BpmnType;
import io.camunda.connector.http.base.model.HttpMethod;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class PropertyUtilTest {

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static HttpOperation opWithAuth(String id, List<HttpAuthentication> authOverride) {
    return new HttpOperation(
        id,
        id,
        HttpFeelBuilder.string().part("/path"),
        HttpMethod.GET,
        null,
        List.of(),
        authOverride);
  }

  private static HttpOperation opNoOverride(String id) {
    return opWithAuth(id, null);
  }

  // ---------------------------------------------------------------------------
  // serverDiscriminatorPropertyGroup
  // ---------------------------------------------------------------------------

  @Test
  void serverDiscriminator_singleServer_shouldBeStringPropertyWithServerUrl() {
    var server = new HttpServerData("https://api.example.com", "Example API");
    var group = serverDiscriminatorPropertyGroup(List.of(server));

    assertThat(group.properties()).hasSize(1);
    var prop = group.properties().getFirst();
    assertThat(prop.getType()).isEqualTo("String");
    assertThat(prop.getValue()).isEqualTo("https://api.example.com");
    assertThat(prop.getId()).isEqualTo("baseUrl");
  }

  @Test
  void serverDiscriminator_noServers_shouldBeStringPropertyWithNoDefaultValue() {
    var group = serverDiscriminatorPropertyGroup(List.of());

    assertThat(group.properties()).hasSize(1);
    var prop = group.properties().getFirst();
    assertThat(prop.getType()).isEqualTo("String");
    assertThat(prop.getId()).isEqualTo("baseUrl");
  }

  @Test
  void serverDiscriminator_nullServers_shouldBehaveLikeNoServers() {
    var group = serverDiscriminatorPropertyGroup(null);

    assertThat(group.properties()).hasSize(1);
    var prop = group.properties().getFirst();
    assertThat(prop.getType()).isEqualTo("String");
    assertThat(prop.getId()).isEqualTo("baseUrl");
  }

  @Test
  void serverDiscriminator_multipleServers_shouldBeDropdown() {
    var servers =
        List.of(
            new HttpServerData("https://api1.example.com", "API 1"),
            new HttpServerData("https://api2.example.com", "API 2"));
    var group = serverDiscriminatorPropertyGroup(servers);

    assertThat(group.properties()).hasSize(1);
    assertThat(group.properties().getFirst().getType()).isEqualTo("Dropdown");
  }

  // ---------------------------------------------------------------------------
  // authPropertyGroup — deduplication
  // ---------------------------------------------------------------------------

  @Test
  void authGroup_allOpsWithSameSingleOverrideAuth_shouldBeUnconditional() {
    // All 3 operations have the same bearer auth override — the Console API scenario
    var bearer = BearerAuth.INSTANCE;
    var op1 = opWithAuth("op1", List.of(bearer));
    var op2 = opWithAuth("op2", List.of(bearer));
    var op3 = opWithAuth("op3", List.of(bearer));

    List<HttpAuthentication> globalAuth = List.of(NoAuth.INSTANCE);
    var result = authPropertyGroup(globalAuth, List.of(op1, op2, op3));

    assertThat(result.unconditional()).isTrue();
    // All properties must have no condition
    assertThat(result.group().properties()).isNotEmpty();
    assertThat(result.group().properties()).allMatch(p -> p.getCondition() == null);
  }

  @Test
  void authGroup_allOpsWithSameSingleOverrideAuth_shouldEmitExactlyOneSetOfProperties() {
    var bearer = BearerAuth.INSTANCE;
    var ops =
        List.of(
            opWithAuth("op1", List.of(bearer)),
            opWithAuth("op2", List.of(bearer)),
            opWithAuth("op3", List.of(bearer)));

    List<HttpAuthentication> globalAuth = List.of(NoAuth.INSTANCE);
    var result = authPropertyGroup(globalAuth, ops);

    // BearerAuth emits: hidden authType discriminator + hidden bearer type setter + bearer token
    // = 3 properties. Should have exactly 3, not 3×3=9
    assertThat(result.group().properties()).hasSize(3);
  }

  @Test
  void authGroup_mixedOps_someGlobalSomeOverride_shouldBeConditional() {
    var bearer = BearerAuth.INSTANCE;
    List<HttpAuthentication> globalAuth = List.of(NoAuth.INSTANCE);

    var opGlobal1 = opNoOverride("op1");
    var opGlobal2 = opNoOverride("op2");
    var opOverride = opWithAuth("op3", List.of(bearer));

    var result = authPropertyGroup(globalAuth, List.of(opGlobal1, opGlobal2, opOverride));

    assertThat(result.unconditional()).isFalse();
    // At least some properties must have conditions
    assertThat(result.group().properties()).anyMatch(p -> p.getCondition() != null);
  }

  @Test
  void authGroup_mixedOps_shouldNotDuplicatePropertiesForSharedAuth() {
    var bearer = BearerAuth.INSTANCE;
    List<HttpAuthentication> globalAuth = List.of(NoAuth.INSTANCE);

    // op1 and op2 both override with bearer — should produce one set of bearer props
    var op1 = opWithAuth("op1", List.of(bearer));
    var op2 = opWithAuth("op2", List.of(bearer));
    var op3 = opNoOverride("op3"); // uses global NoAuth

    var result = authPropertyGroup(globalAuth, List.of(op1, op2, op3));

    // bearer group: 3 props (authType discriminator + bearer type + token)
    // noAuth group: 2 props (authType discriminator + noAuth type)
    // total: 5 — not 3+3+2=8 (the pre-dedup count)
    assertThat(result.group().properties()).hasSize(5);
  }

  @Test
  void authGroup_distinctOauthConfigsWithSameId_shouldNotBeDeduplicated() {
    var contactsOauth = new OAuth2("https://token.example.com", Set.of("read:contacts"));
    var dealsOauth = new OAuth2("https://token.example.com", Set.of("read:deals"));

    var contacts = opWithAuth("contacts", List.of(contactsOauth));
    var deals = opWithAuth("deals", List.of(dealsOauth));

    var result = authPropertyGroup(List.of(NoAuth.INSTANCE), List.of(contacts, deals));

    assertThat(result.unconditional()).isFalse();
    assertThat(result.group().properties())
        .filteredOn(
            property ->
                property.getBinding() instanceof ZeebeInput binding
                    && "authentication.scopes".equals(binding.name()))
        .extracting(Property::getValue)
        .containsExactlyInAnyOrder("read:contacts", "read:deals");
  }

  // ---------------------------------------------------------------------------
  // HttpOutboundElementTemplateBuilder — group ordering
  // ---------------------------------------------------------------------------

  @Test
  void builder_unconditionalAuth_shouldPlaceAuthBeforeOperation() {
    var bearer = BearerAuth.INSTANCE;
    var op1 = opWithAuth("op1", List.of(bearer));
    var op2 = opWithAuth("op2", List.of(bearer));

    List<HttpAuthentication> globalAuth = List.of(NoAuth.INSTANCE);
    ElementTemplate template =
        HttpOutboundElementTemplateBuilder.create()
            .id("test")
            .name("Test")
            .servers(new HttpServerData("https://api.example.com", "Example"))
            .operations(List.of(op1, op2))
            .authentication(globalAuth)
            .elementType(
                new ConnectorElementType(Set.of(BpmnType.TASK), BpmnType.SERVICE_TASK, null, null))
            .build();

    List<String> groupIds = template.groups().stream().map(PropertyGroup::id).toList();
    int authIdx = groupIds.indexOf("authentication");
    int opIdx = groupIds.indexOf("operation");
    assertThat(authIdx).isLessThan(opIdx);
  }

  @Test
  void builder_conditionalAuth_shouldPlaceOperationBeforeAuth() {
    var bearer = BearerAuth.INSTANCE;
    // op1 uses bearer override, op2 uses global NoAuth → mixed → conditional
    var op1 = opWithAuth("op1", List.of(bearer));
    var op2 = opNoOverride("op2");

    List<HttpAuthentication> globalAuth = List.of(NoAuth.INSTANCE);
    ElementTemplate template =
        HttpOutboundElementTemplateBuilder.create()
            .id("test")
            .name("Test")
            .servers(new HttpServerData("https://api.example.com", "Example"))
            .operations(List.of(op1, op2))
            .authentication(globalAuth)
            .elementType(
                new ConnectorElementType(Set.of(BpmnType.TASK), BpmnType.SERVICE_TASK, null, null))
            .build();

    List<String> groupIds = template.groups().stream().map(PropertyGroup::id).toList();
    int authIdx = groupIds.indexOf("authentication");
    int opIdx = groupIds.indexOf("operation");
    assertThat(opIdx).isLessThan(authIdx);
  }

  @Test
  void builder_nullServers_shouldBehaveLikeEmptyServers() {
    var template =
        HttpOutboundElementTemplateBuilder.create()
            .id("test")
            .name("Test")
            .operations(List.of(opNoOverride("op1")))
            .authentication(List.of(NoAuth.INSTANCE))
            .elementType(
                new ConnectorElementType(Set.of(BpmnType.TASK), BpmnType.SERVICE_TASK, null, null))
            .build();

    var serverGroup =
        template.groups().stream().filter(group -> "server".equals(group.id())).findFirst();

    assertThat(serverGroup).isPresent();
    assertThat(serverGroup.orElseThrow().properties()).hasSize(1);
    assertThat(serverGroup.orElseThrow().properties().getFirst().getId()).isEqualTo("baseUrl");
    assertThat(serverGroup.orElseThrow().properties().getFirst().getType()).isEqualTo("String");
  }

  // ---------------------------------------------------------------------------
  // Original test
  // ---------------------------------------------------------------------------

  @Test
  void shouldAddCorrectHeader() {
    // given
    HttpOperationProperty property =
        HttpOperationProperty.createHiddenProperty(
            "Content-Type", Target.HEADER, "", true, "multipart/form-data");
    HttpOperation httpOperation =
        new HttpOperation(
            "POST_/mypath",
            "POST_/mypath",
            HttpFeelBuilder.string().part("/hello"),
            HttpMethod.GET,
            HttpFeelBuilder.string().part("body"),
            List.of(property),
            null);

    // when
    PropertyGroup propertyGroup = parametersPropertyGroup(List.of(httpOperation));
    List<Property> properties = propertyGroup.properties();

    // expected
    assertThat(properties).hasSize(5);

    assertThat(properties)
        .anyMatch(
            element ->
                element.getValue().equals("={Content-Type: Content_Type}")
                    && element.getType().equals("Hidden"));

    assertThat(properties)
        .anyMatch(
            element ->
                element.getId().equals("POST_/mypath_header_Content-Type")
                    && element.getValue().equals("multipart/form-data")
                    && element.getType().equals("Hidden"));
  }
}
