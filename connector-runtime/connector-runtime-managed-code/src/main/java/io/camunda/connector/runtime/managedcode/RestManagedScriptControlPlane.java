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
package io.camunda.connector.runtime.managedcode;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/** REST transport for the orchestration cluster managed-script lifecycle API. */
public final class RestManagedScriptControlPlane implements ManagedScriptControlPlane {

  private static final String DEFAULT_PHYSICAL_TENANT_ID = "default";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
  private static final int MAX_ERROR_BODY_LENGTH = 4_096;

  private final String provider;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public RestManagedScriptControlPlane(final String provider, final ObjectMapper objectMapper) {
    this(provider, HttpClient.newHttpClient(), objectMapper);
  }

  RestManagedScriptControlPlane(
      final String provider, final HttpClient httpClient, final ObjectMapper objectMapper) {
    this.provider = provider;
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<ManagedScriptDeployment> acquireDeployments(
      final CamundaClient client,
      final String physicalTenantId,
      final String worker,
      final int limit,
      final Duration leaseDuration)
      throws Exception {
    final var request =
        new ActivationRequest(
            provider,
            worker,
            leaseDuration.toMillis(),
            limit,
            client.getConfiguration().getDefaultTenantId());
    final var response =
        exchangeJson(
            client,
            physicalTenantId,
            "POST",
            "/managed-script-definitions/activation",
            request,
            ActivationResponse.class);
    return response.definitions().stream()
        .map(
            definition -> {
              try {
                return toDeployment(
                    definition,
                    get(
                        client,
                        physicalTenantId,
                        "/resources/%d/content/binary".formatted(definition.resourceKey())));
              } catch (final Exception e) {
                throw new ArtifactFetchException(definition.resourceKey(), e);
              }
            })
        .toList();
  }

  @Override
  public void renewLease(
      final CamundaClient client,
      final String physicalTenantId,
      final ManagedScriptDeployment deployment,
      final Duration leaseDuration)
      throws Exception {
    exchangeJson(
        client,
        physicalTenantId,
        "POST",
        "/managed-script-definitions/%s/lease".formatted(deployment.deploymentId()),
        new LeaseRenewalRequest(
            deployment.definitionRevision(), deployment.leaseToken(), leaseDuration.toMillis()),
        DefinitionResponse.class);
  }

  @Override
  public ManagedScriptDeployment recordProviderOperation(
      final CamundaClient client,
      final String physicalTenantId,
      final ManagedScriptDeployment deployment,
      final String providerOperationId)
      throws Exception {
    final var response =
        transition(
            client,
            physicalTenantId,
            deployment,
            new TransitionRequest(
                deployment.definitionRevision(),
                deployment.leaseToken(),
                operationId(deployment, "checkpoint"),
                "DEPLOYING",
                providerOperationId,
                null,
                null,
                null,
                false));
    return deployment.withProviderOperation(response.revision(), providerOperationId);
  }

  @Override
  public void completeDeployment(
      final CamundaClient client,
      final String physicalTenantId,
      final ManagedScriptDeployment deployment,
      final ManagedCodeDeploymentResult result)
      throws Exception {
    transition(
        client,
        physicalTenantId,
        deployment,
        new TransitionRequest(
            deployment.definitionRevision(),
            deployment.leaseToken(),
            operationId(deployment, "complete"),
            "READY",
            null,
            result.providerDeploymentId(),
            null,
            null,
            false));
  }

  @Override
  public void failDeployment(
      final CamundaClient client,
      final String physicalTenantId,
      final ManagedScriptDeployment deployment,
      final ManagedCodeDeploymentFailure failure)
      throws Exception {
    transition(
        client,
        physicalTenantId,
        deployment,
        new TransitionRequest(
            deployment.definitionRevision(),
            deployment.leaseToken(),
            operationId(deployment, "fail"),
            "FAILED",
            null,
            null,
            failure.code(),
            failure.message(),
            failure.retryable()));
  }

  private DefinitionResponse transition(
      final CamundaClient client,
      final String physicalTenantId,
      final ManagedScriptDeployment deployment,
      final TransitionRequest request)
      throws Exception {
    return exchangeJson(
        client,
        physicalTenantId,
        "POST",
        "/managed-script-definitions/%s/transitions".formatted(deployment.deploymentId()),
        request,
        DefinitionResponse.class);
  }

  private byte[] get(final CamundaClient client, final String physicalTenantId, final String path)
      throws Exception {
    final var request =
        authenticatedRequest(client, endpoint(client, physicalTenantId, path)).GET();
    return send(request.build()).body();
  }

  private <T> T exchangeJson(
      final CamundaClient client,
      final String physicalTenantId,
      final String method,
      final String path,
      final Object body,
      final Class<T> responseType)
      throws Exception {
    final var request =
        authenticatedRequest(client, endpoint(client, physicalTenantId, path))
            .header("Content-Type", "application/json")
            .method(
                method,
                HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body)));
    return objectMapper.readValue(send(request.build()).body(), responseType);
  }

  private HttpRequest.Builder authenticatedRequest(final CamundaClient client, final URI uri)
      throws IOException {
    final var request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT);
    client.getConfiguration().getCredentialsProvider().applyCredentials(request::header);
    return request;
  }

  private HttpResponse<byte[]> send(final HttpRequest request)
      throws IOException, InterruptedException {
    final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      final var body = new String(response.body(), StandardCharsets.UTF_8);
      throw new IOException(
          "Managed-script control-plane request failed with HTTP %d: %s"
              .formatted(
                  response.statusCode(),
                  body.substring(0, Math.min(body.length(), MAX_ERROR_BODY_LENGTH))));
    }
    return response;
  }

  private static URI endpoint(
      final CamundaClient client, final String physicalTenantId, final String path) {
    final var base = stripTrailingSlash(client.getConfiguration().getRestAddress().toString());
    final var tenantPrefix =
        DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)
            ? "/v2"
            : "/physical-tenants/%s/v2".formatted(encodePathSegment(physicalTenantId));
    return URI.create(base + tenantPrefix + path);
  }

  private static String stripTrailingSlash(final String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private static String encodePathSegment(final String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static ManagedScriptDeployment toDeployment(
      final DefinitionResponse definition, final byte[] artifact) {
    return new ManagedScriptDeployment(
        Long.toString(definition.managedScriptDefinitionKey()),
        definition.revision(),
        definition.leaseToken(),
        definition.provider(),
        definition.tenantId(),
        definition.resourceKey(),
        definition.resourceName(),
        HexFormat.of().parseHex(definition.artifactDigest()),
        definition.language(),
        definition.runtime(),
        artifact,
        Optional.ofNullable(blankToNull(definition.providerOperationId())));
  }

  private static String operationId(
      final ManagedScriptDeployment deployment, final String operation) {
    return "%s:%s:%d"
        .formatted(deployment.deploymentId(), operation, deployment.definitionRevision());
  }

  private static String blankToNull(final String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private record ActivationRequest(
      String provider, String worker, long leaseDuration, int maxDefinitions, String tenantId) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ActivationResponse(List<DefinitionResponse> definitions) {}

  private record LeaseRenewalRequest(long revision, String leaseToken, long leaseDuration) {}

  private record TransitionRequest(
      long revision,
      String leaseToken,
      String operationId,
      String status,
      String providerOperationId,
      String providerDeploymentId,
      String failureCode,
      String failureMessage,
      boolean retryable) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record DefinitionResponse(
      long managedScriptDefinitionKey,
      long revision,
      long resourceKey,
      String resourceName,
      String artifactDigest,
      String language,
      String runtime,
      String provider,
      String leaseToken,
      String providerOperationId,
      String tenantId) {}

  private static final class ArtifactFetchException extends RuntimeException {

    private ArtifactFetchException(final long resourceKey, final Exception cause) {
      super("Failed to fetch managed script resource '%d'".formatted(resourceKey), cause);
    }
  }
}
