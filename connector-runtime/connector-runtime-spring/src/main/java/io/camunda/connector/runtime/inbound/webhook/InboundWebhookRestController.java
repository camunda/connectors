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
import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.HEAD;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.inbound.ActivationCheckResult;
import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy.ForwardErrorToUpstream;
import io.camunda.connector.api.inbound.CorrelationFailureHandlingStrategy.Ignore;
import io.camunda.connector.api.inbound.CorrelationRequest;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.CorrelationResult.Success.MessagePublished;
import io.camunda.connector.api.inbound.CorrelationResult.Success.ProcessInstanceCreated;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.Severity;
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
import io.camunda.connector.runtime.core.inbound.InboundConnectorReportingContext;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable;
import io.camunda.connector.runtime.inbound.webhook.model.HttpServletRequestWebhookProcessingPayload;
import io.grpc.Status;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
public class InboundWebhookRestController {

  private static final Logger LOG = LoggerFactory.getLogger(InboundWebhookRestController.class);

  private final WebhookConnectorRegistry webhookConnectorRegistry;

  @Autowired
  public InboundWebhookRestController(final WebhookConnectorRegistry webhookConnectorRegistry) {
    this.webhookConnectorRegistry = webhookConnectorRegistry;
  }

  protected static ResponseEntity<?> toResponseEntity(WebhookHttpResponse webhookHttpResponse) {
    int status =
        Optional.ofNullable(webhookHttpResponse.statusCode()).orElse(HttpStatus.OK.value());
    HttpHeaders headers = new HttpHeaders();
    Optional.ofNullable(webhookHttpResponse.headers())
        .orElse(Collections.emptyMap())
        .forEach(headers::add);
    return ResponseEntity.status(status)
        .headers(headers)
        .body(escapeValue(webhookHttpResponse.body()));
  }

  protected static Object escapeValue(Object value) {
    return switch (value) {
      case String s -> HtmlUtils.htmlEscape(s);
      case null, default -> value;
    };
  }

  private static io.camunda.connector.api.inbound.webhook.Part mapToCamundaPart(Part part) {
    try {
      return new io.camunda.connector.api.inbound.webhook.Part(
          part.getName(),
          part.getSubmittedFileName(),
          part.getInputStream(),
          part.getContentType());
    } catch (IOException e) {
      LOG.warn("Failed to process part: {}", part.getName(), e);
      return null;
    }
  }

  @RequestMapping(
      method = {GET, HEAD, POST, PUT, DELETE},
      path = "/inbound/{context}")
  public ResponseEntity<?> inbound(
      @PathVariable(value = "context") String context,
      @RequestHeader Map<String, String> headers,
      @RequestBody(required = false) byte[] bodyAsByteArray,
      @RequestParam(required = false) Map<String, String> params,
      HttpServletRequest httpServletRequest)
      throws IOException {
    LOG.trace("Received inbound hook on {}", context);
    return webhookConnectorRegistry
        .getActiveWebhook(context)
        .map(
            connector -> {
              WebhookProcessingPayload payload =
                  new HttpServletRequestWebhookProcessingPayload(
                      httpServletRequest,
                      params,
                      headers,
                      bodyAsByteArray,
                      getParts(httpServletRequest));
              return processWebhook(connector, payload);
            })
        .orElseGet(() -> ResponseEntity.notFound().build());
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
      response = verify(connectorHook, payload, connector.context());
      if (response == null) {
        // when verification was skipped
        // Step 2: trigger and correlate
        connector
            .context()
            .log(
                activity ->
                    activity
                        .withSeverity(Severity.INFO)
                        .withTag(payload.method())
                        .withMessage("URL: " + payload.requestURL()));

        var webhookResult = connectorHook.triggerWebhook(payload);
        // create documents if the connector is activable
        var documents = createDocuments(connector.context(), webhookResult, payload.parts());
        var ctxData = toWebhookTriggerResultContext(webhookResult, documents);
        // correlate
        var correlationResult =
            connector.context().correlate(CorrelationRequest.builder().variables(ctxData).build());
        response = buildResponse(webhookResult, documents, correlationResult);
      }
    } catch (Exception e) {
      connector
          .context()
          .log(
              activity ->
                  activity
                      .withSeverity(Severity.ERROR)
                      .withTag(payload.method())
                      .withMessage("Webhook processing failed"));
      response = buildErrorResponse(e);
    }
    return response;
  }

  private List<Document> createDocuments(
      InboundConnectorContext context,
      WebhookResult webhookResult,
      Collection<io.camunda.connector.api.inbound.webhook.Part> parts) {
    if (!(context.canActivate(webhookResult) instanceof ActivationCheckResult.Success)) {
      return List.of();
    }

    return parts.stream()
        .map(
            part ->
                context.create(
                    DocumentCreationRequest.from(part.inputStream())
                        .fileName(part.submittedFileName())
                        .contentType(part.contentType())
                        .build()))
        .toList();
  }

  protected ResponseEntity<?> verify(
      WebhookConnectorExecutable connectorHook,
      WebhookProcessingPayload payload,
      InboundConnectorReportingContext context) {
    WebhookHttpResponse verificationResponse = connectorHook.verify(payload);
    ResponseEntity<?> response = null;
    if (verificationResponse != null) {
      response = toResponseEntity(verificationResponse);
      context.log(
          activity ->
              activity
                  .withSeverity(Severity.INFO)
                  .withTag(payload.method())
                  .withMessage("Successfully handled a verification request"));
    }
    return response;
  }

  private ResponseEntity<?> buildResponse(
      WebhookResult webhookResult, List<Document> documents, CorrelationResult correlationResult) {
    ResponseEntity<?> response;
    if (correlationResult instanceof CorrelationResult.Success success) {
      response = buildSuccessfulResponse(webhookResult, documents, success);
    } else {
      if (correlationResult instanceof CorrelationResult.Failure failure) {
        switch (failure.handlingStrategy()) {
          case ForwardErrorToUpstream ignored -> response = buildErrorResponse(failure);
          case Ignore ignored -> response = buildSuccessfulResponse(webhookResult, documents, null);
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
      WebhookResult webhookResult,
      List<Document> documents,
      CorrelationResult.Success correlationResult) {
    ResponseEntity<?> response;
    if (webhookResult.response() != null) {
      var processVariablesContext =
          toWebhookResultContext(webhookResult, documents, correlationResult);
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

  private Collection<io.camunda.connector.api.inbound.webhook.Part> getParts(
      HttpServletRequest httpServletRequest) {
    try {
      return httpServletRequest.getParts().stream()
          .map(InboundWebhookRestController::mapToCamundaPart)
          .filter(Objects::nonNull)
          .toList();
    } catch (IOException e) {
      LOG.error("Failed to get parts from request", e);
      throw new RuntimeException("Failed to get parts from request", e);
    } catch (ServletException e) {
      LOG.debug("The request is not multipart/form-data, silently ignoring", e);
      return List.of();
    } catch (IllegalStateException e) {
      LOG.error("Size limits are exceeded or no multipart configuration is provided", e);
      throw new RuntimeException(
          "Size limits are exceeded or no multipart configuration is provided", e);
    }
  }

  // This will be used to correlate data returned from connector.
  // In other words, we pass this data to Zeebe.
  private WebhookTriggerResultContext toWebhookTriggerResultContext(
      WebhookResult processedResult, List<Document> documents) {
    WebhookTriggerResultContext ctx = new WebhookTriggerResultContext(null, null, List.of());
    if (processedResult != null) {
      ctx =
          new WebhookTriggerResultContext(
              new MappedHttpRequest(
                  Optional.ofNullable(processedResult.request().body()).orElse(emptyMap()),
                  Optional.ofNullable(processedResult.request().headers()).orElse(emptyMap()),
                  Optional.ofNullable(processedResult.request().params()).orElse(emptyMap())),
              Optional.ofNullable(processedResult.connectorData()).orElse(emptyMap()),
              documents);
    }
    return ctx;
  }

  // This data will be used to compose a response.
  // In other words, depending on the response body expression,
  // this data may be returned to the webhook caller.
  private WebhookResultContext toWebhookResultContext(
      WebhookResult processedResult,
      List<Document> documents,
      CorrelationResult.Success correlationResult) {
    WebhookResultContext ctx = new WebhookResultContext(null, null, null);
    if (processedResult != null) {
      CorrelationResult.Success correlation = null;
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
              correlation,
              documents);
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
