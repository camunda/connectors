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
package io.camunda.connector.runtime.inbound.webhook;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;
import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;

import com.google.common.util.concurrent.RateLimiter;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy.ForwardErrorToUpstream;
import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy.Ignore;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.CorrelationResult.Success.MessagePublished;
import io.camunda.connector.api.inbound.CorrelationResult.Success.ProcessInstanceCreated;
import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorException;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorException.WebhookSecurityException;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookHttpResponse;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.api.inbound.webhook.WebhookResultContext;
import io.camunda.connector.api.inbound.webhook.WebhookTriggerResultContext;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable;
import io.camunda.connector.runtime.inbound.webhook.model.HttpServletRequestWebhookProcessingPayload;
import io.grpc.Status;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
public class InboundWebhookRestController {

  private static final Logger LOG = LoggerFactory.getLogger(InboundWebhookRestController.class);

  private final WebhookConnectorRegistry webhookConnectorRegistry;

  @Value("${camunda.connector.webhook.max-request-body-bytes:10485760}")
  int maxRequestBodyBytes = 10 * 1024 * 1024;

  @Value("${camunda.connector.webhook.rate-limit.enabled:true}")
  boolean rateLimitEnabled;

  @Value("${camunda.connector.webhook.rate-limit.permits-per-second:1000}")
  double rateLimitPermitsPerSecond = 1000;

  RateLimiter globalRateLimiter;

  @Autowired
  public InboundWebhookRestController(final WebhookConnectorRegistry webhookConnectorRegistry) {
    this.webhookConnectorRegistry = webhookConnectorRegistry;
  }

  @PostConstruct
  void validateWebhookConfig() {
    if (maxRequestBodyBytes < 0) {
      throw new IllegalStateException(
          "camunda.connector.webhook.max-request-body-bytes must not be negative, but was: "
              + maxRequestBodyBytes);
    }
    if (rateLimitEnabled
        && !(Double.isFinite(rateLimitPermitsPerSecond) && rateLimitPermitsPerSecond > 0)) {
      throw new IllegalStateException(
          "camunda.connector.webhook.rate-limit.permits-per-second must be a positive, finite "
              + "number when camunda.connector.webhook.rate-limit.enabled is true, but was: "
              + rateLimitPermitsPerSecond);
    }
    if (rateLimitEnabled) {
      globalRateLimiter = RateLimiter.create(rateLimitPermitsPerSecond);
    }
  }

  protected static ResponseEntity<?> toResponseEntity(WebhookHttpResponse webhookHttpResponse) {
    int status =
        Optional.ofNullable(webhookHttpResponse.statusCode()).orElse(HttpStatus.OK.value());
    HttpHeaders headers = new HttpHeaders();
    Optional.ofNullable(webhookHttpResponse.headers())
        .orElse(Collections.emptyMap())
        .forEach(headers::add);

    String contentType =
        Optional.ofNullable(webhookHttpResponse.headers())
            .flatMap(
                h ->
                    h.entrySet().stream()
                        .filter(e -> e.getKey().equalsIgnoreCase(HttpHeaders.CONTENT_TYPE))
                        .map(Map.Entry::getValue)
                        .findFirst())
            .orElse(null);
    Object body =
        isXmlContentType(contentType)
            ? webhookHttpResponse.body()
            : escapeValue(webhookHttpResponse.body());

    return ResponseEntity.status(status).headers(headers).body(body);
  }

  protected static Object escapeValue(Object value) {
    return switch (value) {
      case String s -> HtmlUtils.htmlEscape(s);
      case null, default -> value;
    };
  }

  private static boolean isXmlContentType(String contentType) {
    if (contentType == null) {
      return false;
    }
    String lowerContentType = contentType.toLowerCase();
    return lowerContentType.contains("application/xml") || lowerContentType.contains("text/xml");
  }

  @RequestMapping(
      method = {GET, POST, PUT, DELETE},
      path = "/inbound/{context}")
  public ResponseEntity<?> inbound(
      @PathVariable(value = "context") String context,
      @RequestHeader Map<String, String> headers,
      HttpServletRequest httpServletRequest)
      throws IOException {
    LOG.trace("Received inbound hook on {}", context);
    var connectorOpt = webhookConnectorRegistry.getActiveWebhook(context);
    if (connectorOpt.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    var connector = connectorOpt.get();

    if (rateLimitEnabled && !globalRateLimiter.tryAcquire()) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }

    // Servlet multipart parsing consumes the raw stream before the controller runs, so multipart
    // requests carry no raw body. Other content types retain the original bytes needed by HMAC
    // verification.
    boolean isMultipartFormData =
        WebhookFilterPaths.isMultipartFormData(httpServletRequest.getContentType());
    byte[] bodyAsByteArray = isMultipartFormData ? null : readBoundedBody(httpServletRequest);
    if (!isMultipartFormData && bodyAsByteArray == null) {
      return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
    }
    Map<String, String> params = extractQueryParams(httpServletRequest.getQueryString());

    WebhookProcessingPayload payload =
        new HttpServletRequestWebhookProcessingPayload(
            httpServletRequest, params, headers, bodyAsByteArray);
    return processWebhook(connector, payload);
  }

  public ResponseEntity<?> inbound(
      String context,
      Map<String, String> headers,
      byte[] bodyAsByteArray,
      Map<String, String> params,
      HttpServletRequest httpServletRequest) {
    LOG.trace("Received inbound hook on {}", context);
    return webhookConnectorRegistry
        .getActiveWebhook(context)
        .map(
            connector -> {
              WebhookProcessingPayload payload =
                  new HttpServletRequestWebhookProcessingPayload(
                      httpServletRequest, params, headers, bodyAsByteArray);
              return processWebhook(connector, payload);
            })
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  private byte[] readBoundedBody(HttpServletRequest httpServletRequest) throws IOException {
    var inputStream = httpServletRequest.getInputStream();
    byte[] body = inputStream.readNBytes(maxRequestBodyBytes);
    if (inputStream.read() != -1) {
      return null;
    }
    return body;
  }

  private ResponseEntity<?> processWebhook(
      RegisteredExecutable.Activated connector, WebhookProcessingPayload payload) {
    ResponseEntity<?> response;
    try {
      WebhookConnectorExecutable connectorHook =
          (WebhookConnectorExecutable) connector.executable();
      // Step 1: verification
      // This is required for cases, when we need to get a message from an external source
      // but at the same time, not triggering correlation
      // Such use-case can be echoing webhook verification challenge
      response = verify(connectorHook, payload);
      if (response == null) {
        // when verification was skipped
        // Step 2: trigger and correlate
        var webhookResult = connectorHook.triggerWebhook(payload);
        var ctxData = toWebhookTriggerResultContext(webhookResult);
        var correlationResult = connector.context().correlateWithResult(ctxData);
        response = buildResponse(webhookResult, correlationResult);
      }
    } catch (Exception e) {
      LOG.info("Webhook: {} failed with exception", connector.context().getDefinition(), e);
      response = buildErrorResponse(e);
    }
    return response;
  }

  protected ResponseEntity<?> verify(
      WebhookConnectorExecutable connectorHook, WebhookProcessingPayload payload) {
    WebhookHttpResponse verificationResponse = connectorHook.verify(payload);
    ResponseEntity<?> response = null;
    if (verificationResponse != null) {
      response = toResponseEntity(verificationResponse);
    }
    return response;
  }

  private ResponseEntity<?> buildResponse(
      WebhookResult webhookResult, CorrelationResult correlationResult) {
    ResponseEntity<?> response;
    if (correlationResult instanceof CorrelationResult.Success success) {
      response = buildSuccessfulResponse(webhookResult, success);
    } else {
      if (correlationResult instanceof CorrelationResult.Failure failure) {
        switch (failure.handlingStrategy()) {
          case ForwardErrorToUpstream ignored -> response = buildErrorResponse(failure);
          case Ignore ignored -> response = buildSuccessfulResponse(webhookResult, null);
        }
      } else {
        throw new IllegalStateException("Illegal correlation result : " + correlationResult);
      }
    }
    return response;
  }

  private ResponseEntity<?> buildErrorResponse(CorrelationResult.Failure failure) {
    ResponseEntity<?> response;
    if (failure instanceof CorrelationResult.Failure.Other) {
      response = ResponseEntity.internalServerError().build();
    } else if (failure instanceof CorrelationResult.Failure.ZeebeClientStatus zeebeClientStatus) {
      response =
          switch (Status.Code.valueOf(zeebeClientStatus.status())) {
            case CANCELLED -> ResponseEntity.status(499).body(failure);
            case UNKNOWN, INTERNAL, DATA_LOSS -> ResponseEntity.status(500).body(failure);
            case INVALID_ARGUMENT -> ResponseEntity.status(400).body(failure);
            case DEADLINE_EXCEEDED -> ResponseEntity.status(504).body(failure);
            case NOT_FOUND -> ResponseEntity.status(404).body(failure);
            case ALREADY_EXISTS, ABORTED -> ResponseEntity.status(409).body(failure);
            case PERMISSION_DENIED -> ResponseEntity.status(403).body(failure);
            case RESOURCE_EXHAUSTED -> ResponseEntity.status(429).body(failure);
            case FAILED_PRECONDITION -> ResponseEntity.status(412).body(failure);
            case OUT_OF_RANGE -> ResponseEntity.status(416).body(failure);
            case UNIMPLEMENTED -> ResponseEntity.status(501).body(failure);
            case UNAVAILABLE -> ResponseEntity.status(503).body(failure);
            case UNAUTHENTICATED -> ResponseEntity.status(401).body(failure);
            default -> ResponseEntity.unprocessableEntity().body(failure);
          };
    } else {
      response = ResponseEntity.unprocessableEntity().body(failure);
    }
    return response;
  }

  private ResponseEntity<?> buildSuccessfulResponse(
      WebhookResult webhookResult, CorrelationResult.Success correlationResult) {
    ResponseEntity<?> response;
    if (webhookResult.response() != null) {
      var processVariablesContext = toWebhookResultContext(webhookResult, correlationResult);
      var httpResponseData = webhookResult.response().apply(processVariablesContext);
      if (httpResponseData != null) {
        response = toResponseEntity(httpResponseData);
      } else {
        response = ResponseEntity.ok().build();
      }
    } else {
      response = ResponseEntity.ok().build();
    }
    return response;
  }

  protected ResponseEntity<?> buildErrorResponse(Exception e) {
    ResponseEntity<?> response;
    if (e instanceof FeelEngineWrapperException feelEngineWrapperException) {
      var error =
          new FeelExpressionErrorResponse(
              feelEngineWrapperException.getReason(), feelEngineWrapperException.getExpression());
      response = ResponseEntity.unprocessableEntity().body(error);
    } else if (e instanceof ConnectorException connectorException) {
      if (e instanceof WebhookConnectorException webhookConnectorException) {
        response = handleWebhookConnectorException(webhookConnectorException);
      } else {
        response =
            ResponseEntity.unprocessableEntity()
                .body(
                    new ErrorResponse(
                        connectorException.getErrorCode(), connectorException.getMessage()));
      }
    } else {
      response = ResponseEntity.internalServerError().build();
    }
    return response;
  }

  private static Map<String, String> extractQueryParams(String queryString) {
    if (queryString == null || queryString.isBlank()) {
      return emptyMap();
    }
    return Arrays.stream(queryString.split("&"))
        .map(pair -> pair.split("=", 2))
        .filter(parts -> parts.length > 0 && !parts[0].isBlank())
        .collect(
            toMap(
                parts -> URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                parts ->
                    parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "",
                (a, b) -> a));
  }

  // This will be used to correlate data returned from connector.
  // In other words, we pass this data to Zeebe.
  private WebhookTriggerResultContext toWebhookTriggerResultContext(WebhookResult processedResult) {
    WebhookTriggerResultContext ctx = new WebhookTriggerResultContext(null, null);
    if (processedResult != null) {
      ctx =
          new WebhookTriggerResultContext(
              new MappedHttpRequest(
                  Optional.ofNullable(processedResult.request().body()).orElse(emptyMap()),
                  Optional.ofNullable(processedResult.request().headers()).orElse(emptyMap()),
                  Optional.ofNullable(processedResult.request().params()).orElse(emptyMap())),
              Optional.ofNullable(processedResult.connectorData()).orElse(emptyMap()));
    }
    return ctx;
  }

  // This data will be used to compose a response.
  // In other words, depending on the response body expression,
  // this data may be returned to the webhook caller.
  private WebhookResultContext toWebhookResultContext(
      WebhookResult processedResult, CorrelationResult.Success correlationResult) {
    WebhookResultContext ctx = new WebhookResultContext(null, null, null);
    if (processedResult != null) {
      Object correlation = null;
      if (correlationResult instanceof ProcessInstanceCreated
          || correlationResult instanceof MessagePublished) {
        correlation = correlationResult;
      }
      ctx =
          new WebhookResultContext(
              new MappedHttpRequest(
                  Optional.ofNullable(processedResult.request().body()).orElse(emptyMap()),
                  Optional.ofNullable(processedResult.request().headers()).orElse(emptyMap()),
                  Optional.ofNullable(processedResult.request().params()).orElse(emptyMap())),
              Optional.ofNullable(processedResult.connectorData()).orElse(emptyMap()),
              Optional.ofNullable(correlation).orElse(emptyMap()));
    }
    return ctx;
  }

  private ResponseEntity<?> handleWebhookConnectorException(WebhookConnectorException e) {
    var status = HttpStatus.valueOf(e.getStatusCode());
    ResponseEntity response = ResponseEntity.status(status).build();
    if (e instanceof WebhookSecurityException) {
      LOG.warn("Webhook failed with security-related exception", e);
      // no message will be included for security reasons
      response = ResponseEntity.status(status).body(null);
    }
    if (status.is5xxServerError()) {
      LOG.error("Webhook failed with exception", e);
      // no message will be included for security reasons
      response = ResponseEntity.status(status).body(null);
    }
    if (status.is4xxClientError()) {
      response = ResponseEntity.status(status).body(new GenericErrorResponse(e.getMessage()));
    }
    return response;
  }
}
