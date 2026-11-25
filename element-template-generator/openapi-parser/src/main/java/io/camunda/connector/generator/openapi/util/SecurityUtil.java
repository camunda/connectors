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
package io.camunda.connector.generator.openapi.util;

import io.camunda.connector.generator.dsl.http.HttpAuthentication;
import io.camunda.connector.generator.dsl.http.HttpAuthentication.NoAuth;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityScheme.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurityUtil {

  private static final Logger LOG = LoggerFactory.getLogger(SecurityUtil.class);

  /**
   * Two security schemes with the same authentication ID but a different configuration, which one
   * template can't offer side by side.
   */
  public static class ConflictingSecuritySchemesException extends IllegalArgumentException {
    ConflictingSecuritySchemesException(String message) {
      super(message);
    }
  }

  public static List<HttpAuthentication> parseAuthentication(
      List<SecurityRequirement> security, Components components) {

    boolean operationSecuritySchemeOverwritesGlobalWithNoAuth =
        (security != null && security.isEmpty());
    boolean operationSecuritySchemeAndGlobalSchemeIsEmtpy =
        (security == null && components == null)
            || (security == null && components.getSecuritySchemes() == null);

    if (operationSecuritySchemeOverwritesGlobalWithNoAuth
        || operationSecuritySchemeAndGlobalSchemeIsEmtpy) {
      LOG.info("No security schemes found, providing default security section");
      return List.of(
          new HttpAuthentication.NoAuth(),
          new HttpAuthentication.BasicAuth(""),
          new HttpAuthentication.BearerAuth(),
          new HttpAuthentication.OAuth2("", Set.of("")),
          new HttpAuthentication.ApiKey("", "", ""));
    }
    boolean operationSecuritySchemeIsEmptyFallbackToGlobal = (security == null);
    if (operationSecuritySchemeIsEmptyFallbackToGlobal) {
      return Collections.emptyList();
    }

    List<HttpAuthentication> result = new ArrayList<>();
    AtomicBoolean foundErrors = new AtomicBoolean(false);

    security.stream()
        .filter(requirement -> {
          if (requirement.isEmpty()) {
            result.add(NoAuth.INSTANCE);
            return false;
          }
          return true;
        })
        .flatMap(s -> s.entrySet().stream())
        .map(schemeRef -> {
          var customScopes = new HashSet<>(schemeRef.getValue());
          var scheme = components.getSecuritySchemes().get(schemeRef.getKey());
          try {
            return transformToAuthentication(scheme, customScopes, schemeRef.getKey());
          } catch (Exception e) {
            foundErrors.set(true);
            LOG.warn("Could not parse security scheme {}", schemeRef.getKey(), e);
            return null;
          }
        })
        .filter(Objects::nonNull)
        .forEach(result::add);

    if (result.isEmpty() && foundErrors.get()) {
      throw new IllegalArgumentException(
          "Security schemes are not supported by the REST Connector");
    }
    return deduplicate(result);
  }

  /**
   * A security scheme can appear in more than one security requirement, for example {@code
   * security: [{basicAuth: []}, {basicAuth: []}]}, or a scheme shared by two alternatives. The
   * template property IDs of an authentication are derived from its {@link
   * HttpAuthentication#id()}, so keeping more than one entry with the same ID would produce
   * duplicate properties.
   *
   * <p>Identical entries are merged. OAuth2 entries with the same token URL are merged into one
   * entry that requests the scopes of both. Any other two entries with the same ID describe
   * different configurations that one template can't offer, so they are rejected.
   */
  private static List<HttpAuthentication> deduplicate(List<HttpAuthentication> authentications) {
    Map<String, HttpAuthentication> byId = new LinkedHashMap<>();
    for (var authentication : authentications) {
      byId.merge(authentication.id(), authentication, SecurityUtil::mergeSameId);
    }
    return new ArrayList<>(byId.values());
  }

  private static HttpAuthentication mergeSameId(
      HttpAuthentication existing, HttpAuthentication duplicate) {
    if (existing instanceof HttpAuthentication.OAuth2 first
        && duplicate instanceof HttpAuthentication.OAuth2 second
        && Objects.equals(first.tokenUrl(), second.tokenUrl())) {
      Set<String> scopes = new LinkedHashSet<>(first.scopes());
      scopes.addAll(second.scopes());
      return new HttpAuthentication.OAuth2(first.tokenUrl(), scopes);
    }
    if (isSameConfiguration(existing, duplicate)) {
      return existing;
    }
    throw new ConflictingSecuritySchemesException(
        "Conflicting security schemes: more than one "
            + existing.label()
            + " configuration with ID '"
            + existing.id()
            + "'");
  }

  private static boolean isSameConfiguration(HttpAuthentication a, HttpAuthentication b) {
    return switch (a) {
      // records: compare all components
      case HttpAuthentication.ApiKey apiKey -> apiKey.equals(b);
      case HttpAuthentication.OAuth2 oauth2 -> oauth2.equals(b);
      // no configuration beyond the ID (basic auth includes its key in the ID)
      default -> a.getClass().equals(b.getClass());
    };
  }

  private static HttpAuthentication transformToAuthentication(
      SecurityScheme scheme, Set<String> customScopes, String key) {

    if (Type.HTTP.equals(scheme.getType())) {
      if (scheme.getScheme().equals("basic")) {
        return new HttpAuthentication.BasicAuth(key);
      } else if (scheme.getScheme().equals("bearer")) {
        return new HttpAuthentication.BearerAuth();
      } else {
        throw new IllegalArgumentException(
            "SecurityScheme scheme " + scheme.getScheme() + " is not supported");
      }
    } else if (Type.OAUTH2.equals(scheme.getType())) {
      var clientCredentialsFlow = scheme.getFlows().getClientCredentials();
      if (clientCredentialsFlow == null) {
        throw new IllegalArgumentException("Only client credentials flow is supported for OAuth2");
      }
      var scopes =
          (customScopes != null && !customScopes.isEmpty())
              ? customScopes
              : clientCredentialsFlow.getScopes().keySet();
      return new HttpAuthentication.OAuth2(clientCredentialsFlow.getTokenUrl(), scopes);
    } else if (Type.APIKEY.equals(scheme.getType())) {
      SecurityScheme.In inType = scheme.getIn();
      if (SecurityScheme.In.HEADER.equals(inType)) {
        return new HttpAuthentication.ApiKey("headers", scheme.getName(), "");
      } else if (SecurityScheme.In.QUERY.equals(inType)) {
        return new HttpAuthentication.ApiKey("query", scheme.getName(), "");
      }
      throw new IllegalArgumentException("In: " + inType + " is not supported for apiKey");
    } else {
      throw new IllegalArgumentException(
          "SecurityScheme type " + scheme.getType() + " is not supported");
    }
  }
}
