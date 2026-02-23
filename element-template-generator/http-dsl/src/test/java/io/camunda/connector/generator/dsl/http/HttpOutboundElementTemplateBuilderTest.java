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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorElementType;
import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeProperty;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeTaskHeader;
import io.camunda.connector.generator.dsl.http.HttpAuthentication.BasicAuth;
import io.camunda.connector.generator.dsl.http.HttpAuthentication.BearerAuth;
import io.camunda.connector.generator.dsl.http.HttpAuthentication.NoAuth;
import io.camunda.connector.generator.dsl.http.HttpAuthentication.OAuth2;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty.Target;
import io.camunda.connector.generator.java.annotation.BpmnType;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.http.base.model.HttpMethod;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class HttpOutboundElementTemplateBuilderTest {

  @Test
  void propertyOrder() {
    // given
    var operation =
        HttpOperation.builder()
            .id("someGetRequest")
            .label("Some GET request")
            .method(HttpMethod.GET)
            .pathFeelExpression(HttpFeelBuilder.string().part("/examples/").property("exampleId"))
            .properties(
                HttpOperationProperty.createStringProperty(
                    "exampleId", Target.PATH, "Example ID", true, "42"),
                HttpOperationProperty.createStringProperty(
                    "exampleName", Target.QUERY, "Example name", false, "foo"),
                HttpOperationProperty.createStringProperty(
                    "exampleDescription", Target.HEADER, "Example description", false, "bar"))
            .build();

    // when
    var template = buildTemplate(List.of(), List.of(), List.of(operation));
    var properties = template.properties();

    // then
    var urlProperty = findByBindingName("url", properties);
    var queryParametersProperty = findByBindingName("queryParameters", properties);
    var headersProperty = findByBindingName("headers", properties);
    var exampleIdProperty = findByBindingName("exampleId", properties);
    var exampleNameProperty = findByBindingName("exampleName", properties);
    var exampleDescriptionProperty = findByBindingName("exampleDescription", properties);

    assertThat(properties.indexOf(exampleIdProperty)).isLessThan(properties.indexOf(urlProperty));
    assertThat(properties.indexOf(exampleNameProperty))
        .isLessThan(properties.indexOf(queryParametersProperty));
    assertThat(properties.indexOf(exampleDescriptionProperty))
        .isLessThan(properties.indexOf(headersProperty));
  }

  @Test
  void resultExpressionPresent() {
    var template = buildTemplate(List.of(), List.of(), List.of());
    var properties = template.properties();
    assertThat(properties.stream().anyMatch(p -> "resultExpression".equals(p.getId()))).isTrue();
    var resultExpression = findById("resultExpression", properties);
    assertThat(resultExpression.getType()).isEqualTo("Text");
    assertThat(resultExpression.getBinding()).isInstanceOf(ZeebeTaskHeader.class);
    assertThat(resultExpression.getFeel()).isEqualTo(FeelMode.required);
  }

  @Test
  void resultVariablePresent() {
    var template = buildTemplate(List.of(), List.of(), List.of());
    var properties = template.properties();
    assertThat(properties.stream().anyMatch(p -> "resultVariable".equals(p.getId()))).isTrue();
    var resultVariable = findById("resultVariable", properties);
    assertThat(resultVariable.getType()).isEqualTo("String");
    assertThat(resultVariable.getBinding()).isInstanceOf(ZeebeTaskHeader.class);
  }

  @Test
  void errorExpressionPresent() {
    var template = buildTemplate(List.of(), List.of(), List.of());
    var properties = template.properties();
    assertThat(properties.stream().anyMatch(p -> "errorExpression".equals(p.getId()))).isTrue();
    var errorExpression = findById("errorExpression", properties);
    assertThat(errorExpression.getType()).isEqualTo("Text");
    assertThat(errorExpression.getFeel()).isEqualTo(FeelMode.required);
    assertThat(errorExpression.getBinding()).isInstanceOf(ZeebeTaskHeader.class);
    assertThat(errorExpression.getFeel()).isEqualTo(FeelMode.required);
  }

  @Nested
  class ServerProperties {

    @Test
    void multipleServers_discriminatorExpected() {
      // given
      var servers =
          List.of(
              new HttpServerData("https://example.com", "Example server"),
              new HttpServerData("https://example.org", "Example server"));

      // when
      var template = buildTemplate(servers, List.of(), List.of());
      var properties = template.properties();

      // then
      var serverProperty = findByBindingName("baseUrl", properties);
      assertThat(serverProperty.getType()).isEqualTo("Dropdown");
      assertThat(serverProperty.getBinding()).isInstanceOf(ZeebeInput.class);
      assertThat(((DropdownProperty) serverProperty).getChoices())
          .containsExactly(
              new DropdownChoice("Example server", "https://example.com"),
              new DropdownChoice("Example server", "https://example.org"));
      assertThat(serverProperty.getValue()).isEqualTo("https://example.com");
    }

    @Test
    void noServers_visiblePropertyExpected() {
      // given
      List<HttpServerData> servers = List.of();

      // when
      var template = buildTemplate(servers, List.of(), List.of());
      var properties = template.properties();

      // then
      var serverProperty = findByBindingName("baseUrl", properties);
      assertThat(serverProperty.getType()).isEqualTo("String");
      assertThat(serverProperty.getBinding()).isInstanceOf(ZeebeInput.class);
      assertThat(serverProperty.getConstraints().notEmpty()).isTrue();
    }

    @Test
    void singleServer_noVisibleExpected() {
      // given
      var servers = List.of(new HttpServerData("https://example.com", "Example server"));

      // when
      var template = buildTemplate(servers, List.of(), List.of());
      var properties = template.properties();

      // then
      var serverProperty = findByBindingName("baseUrl", properties);
      assertThat(serverProperty.getType()).isEqualTo("Hidden");
      assertThat(serverProperty.getBinding()).isInstanceOf(ZeebeInput.class);
      assertThat(serverProperty.getValue()).isEqualTo("https://example.com");
    }
  }

  @Nested
  class Operations {

    @Test
    void multipleOperations_discriminatorExpected() {
      // given
      var operations = List.of(getOperation(), postOperation());

      // when
      var template = buildTemplate(List.of(), List.of(), operations);
      var properties = template.properties();

      // then
      var operationProperty = findByBindingName("operationId", properties);
      assertThat(operationProperty.getType()).isEqualTo("Dropdown");
      assertThat(operationProperty.getBinding()).isInstanceOf(ZeebeInput.class);
      assertThat(((DropdownProperty) operationProperty).getChoices())
          .containsExactly(
              new DropdownChoice("Some GET request", "someGetRequest"),
              new DropdownChoice("Some POST request", "somePostRequest"));
      assertThat(operationProperty.getValue()).isEqualTo("someGetRequest");
    }

    @Test
    void singleOperation_noVisibleExpected() {
      // given
      var operations = List.of(getOperation());

      // when
      var template = buildTemplate(List.of(), List.of(), operations);
      var properties = template.properties();

      // then
      var operationProperty = findByBindingName("operationId", properties);
      assertThat(operationProperty.getType()).isEqualTo("Hidden");
      assertThat(operationProperty.getBinding()).isInstanceOf(ZeebeInput.class);
      assertThat(operationProperty.getValue()).isEqualTo("someGetRequest");
    }

    private HttpOperation getOperation() {
      return HttpOperation.builder()
          .id("someGetRequest")
          .label("Some GET request")
          .method(HttpMethod.GET)
          .pathFeelExpression(HttpFeelBuilder.string().part("/examples/").property("exampleId"))
          .properties(
              HttpOperationProperty.createStringProperty(
                  "exampleId", Target.PATH, "Example ID", true, "42"),
              HttpOperationProperty.createStringProperty(
                  "exampleName", Target.QUERY, "Example name", false, "foo"),
              HttpOperationProperty.createStringProperty(
                  "exampleDescription", Target.HEADER, "Example description", false, "bar"))
          .build();
    }

    private HttpOperation postOperation() {
      return HttpOperation.builder()
          .id("somePostRequest")
          .label("Some POST request")
          .method(HttpMethod.POST)
          .pathFeelExpression(HttpFeelBuilder.string().part("/examples/").property("exampleId"))
          .properties(
              HttpOperationProperty.createStringProperty(
                  "exampleId", Target.PATH, "Example ID", true, "42"),
              HttpOperationProperty.createStringProperty(
                  "exampleName", Target.QUERY, "Example name", false, "foo"),
              HttpOperationProperty.createStringProperty(
                  "exampleDescription", Target.HEADER, "Example description", false, "bar"))
          .build();
    }
  }

  @Nested
  class Authentication {

    @Test
    void multipleAuths_dropdown() {
      // given
      var auths = List.of(NoAuth.INSTANCE, BasicAuth.of("test"));

      // when
      var template = buildTemplate(List.of(), auths, List.of());
      var properties = template.properties();

      // then
      var authProperty = findByBindingName("authentication.dropdown", properties);
      assertThat(authProperty.getType()).isEqualTo("Dropdown");
      assertThat(authProperty.getBinding()).isInstanceOf(ZeebeProperty.class);
      var basicAuthProperty = findByBindingName("authentication.type", properties);
      assertThat(basicAuthProperty.getBinding()).isInstanceOf(ZeebeInput.class);
      assertThat(((DropdownProperty) authProperty).getChoices())
          .containsExactly(
              new DropdownChoice("None", "noAuth"),
              new DropdownChoice("Basic (test)", "basic.test"));
    }

    @Test
    void singleAuth_noDropdown() {
      // given
      List<HttpAuthentication> auths = List.of(NoAuth.INSTANCE);

      // when
      var template = buildTemplate(List.of(), auths, List.of());
      var properties = template.properties();

      // then
      var authProperty = findByBindingName("authentication.type", properties);
      assertThat(authProperty.getType()).isEqualTo("Hidden");
      assertThat(authProperty.getBinding()).isInstanceOf(ZeebeInput.class);
      assertThat(authProperty.getValue()).isEqualTo("noAuth");
    }

    @Test
    void oauth2Auth() {
      // given
      Set<String> scopes = new LinkedHashSet<>();
      scopes.add("scope1");
      scopes.add("scope2");
      List<HttpAuthentication> auths = List.of(new OAuth2("https://my-token-endpoint", scopes));

      // when
      var template = buildTemplate(List.of(), auths, List.of());
      var properties = template.properties();

      // then
      var authProperty = findByBindingName("authentication.type", properties);
      assertThat(authProperty.getType()).isEqualTo("Hidden");
      assertThat(authProperty.getBinding()).isInstanceOf(ZeebeInput.class);
      assertThat(authProperty.getValue()).isEqualTo("oauth-client-credentials-flow");

      var tokenUrlProperty = findByBindingName("authentication.oauthTokenEndpoint", properties);
      assertThat(tokenUrlProperty.getType()).isEqualTo("String");
      assertThat(tokenUrlProperty.getBinding()).isInstanceOf(ZeebeInput.class);
      assertThat(tokenUrlProperty.getValue()).isEqualTo("https://my-token-endpoint");

      var scopesProperty = findByBindingName("authentication.scopes", properties);
      assertThat(scopesProperty.getType()).isEqualTo("String");
      assertThat(scopesProperty.getBinding()).isInstanceOf(ZeebeInput.class);
      assertThat(scopesProperty.getValue()).isEqualTo("scope1 scope2");

      var clientIdProperty = findByBindingName("authentication.clientId", properties);
      assertThat(clientIdProperty.getType()).isEqualTo("String");
      assertThat(clientIdProperty.getBinding()).isInstanceOf(ZeebeInput.class);

      var clientSecretProperty = findByBindingName("authentication.clientSecret", properties);
      assertThat(clientSecretProperty.getType()).isEqualTo("String");
      assertThat(clientSecretProperty.getBinding()).isInstanceOf(ZeebeInput.class);
    }

    @Test
    void basicAuth() {
      // given
      List<HttpAuthentication> auths = List.of(BasicAuth.of("test"));

      // when
      var template = buildTemplate(List.of(), auths, List.of());
      var properties = template.properties();

      // then
      var authProperty = findByBindingName("authentication.type", properties);
      assertThat(authProperty.getType()).isEqualTo("Hidden");
      assertThat(authProperty.getBinding()).isInstanceOf(ZeebeInput.class);
      assertThat(authProperty.getValue()).isEqualTo("basic");

      var usernameProperty = findByBindingName("authentication.username", properties);
      assertThat(usernameProperty.getType()).isEqualTo("String");
      assertThat(usernameProperty.getBinding()).isInstanceOf(ZeebeInput.class);

      var passwordProperty = findByBindingName("authentication.password", properties);
      assertThat(passwordProperty.getType()).isEqualTo("String");
      assertThat(passwordProperty.getBinding()).isInstanceOf(ZeebeInput.class);
    }

    @Test
    void bearerTokenAuth() {
      // given
      List<HttpAuthentication> auths = List.of(BearerAuth.INSTANCE);

      // when
      var template = buildTemplate(List.of(), auths, List.of());
      var properties = template.properties();

      // then
      var authProperty = findByBindingName("authentication.type", properties);
      assertThat(authProperty.getType()).isEqualTo("Hidden");
      assertThat(authProperty.getValue()).isEqualTo("bearer");
      assertThat(authProperty.getBinding()).isInstanceOf(ZeebeInput.class);

      var tokenProperty = findByBindingName("authentication.token", properties);
      assertThat(tokenProperty.getType()).isEqualTo("String");
      assertThat(tokenProperty.getBinding()).isInstanceOf(ZeebeInput.class);
    }
  }

  ElementTemplate buildTemplate(
      List<HttpServerData> servers,
      List<HttpAuthentication> authentications,
      List<HttpOperation> operations) {
    if (operations.isEmpty()) {
      operations =
          List.of(
              HttpOperation.builder()
                  .id("someGetRequest")
                  .label("Some GET request")
                  .method(HttpMethod.GET)
                  .pathFeelExpression(
                      HttpFeelBuilder.string().part("/examples/").property("exampleId"))
                  .properties(
                      HttpOperationProperty.createStringProperty(
                          "exampleId", Target.PATH, "Example ID", true, "42"),
                      HttpOperationProperty.createStringProperty(
                          "exampleName", Target.QUERY, "Example name", false, "foo"),
                      HttpOperationProperty.createStringProperty(
                          "exampleDescription", Target.HEADER, "Example description", false, "bar"))
                  .build());
    }

    return HttpOutboundElementTemplateBuilder.create()
        .id("testTemplate")
        .name("Test template")
        .description("My test template")
        .documentationRef("https://docs.camunda.io")
        .version(42)
        .servers(servers)
        .authentication(authentications)
        .operations(operations)
        .elementType(
            new ConnectorElementType(Set.of(BpmnType.TASK), BpmnType.SERVICE_TASK, null, null))
        .build();
  }

  private Property findByBindingName(String name, List<Property> properties) {
    return properties.stream()
        .filter(
            p -> {
              Object binding = p.getBinding();
              return (binding instanceof ZeebeInput
                      && ((ZeebeInput) p.getBinding()).name().equals(name))
                  || (binding instanceof ZeebeProperty
                      && ((ZeebeProperty) p.getBinding()).name().equals(name));
            })
        .findFirst()
        .get();
  }

  private Property findById(String id, List<Property> properties) {
    return properties.stream().filter(p -> id.equals(p.getId())).findFirst().get();
  }
}
