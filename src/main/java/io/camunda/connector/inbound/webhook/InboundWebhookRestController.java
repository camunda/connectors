package io.camunda.connector.inbound.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.inbound.feel.FeelEngineWrapper;
import io.camunda.connector.inbound.registry.InboundConnectorRegistry;
import io.camunda.connector.inbound.security.signature.HMACAlgoCustomerChoice;
import io.camunda.connector.inbound.security.signature.HMACSignatureValidator;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

@RestController
public class InboundWebhookRestController {

  private static final Logger LOG = LoggerFactory.getLogger(InboundWebhookRestController.class);

  private final InboundConnectorRegistry registry;
  private final ZeebeClient zeebeClient;
  private final FeelEngineWrapper feelEngine;
  private final ObjectMapper jsonMapper;

  public InboundWebhookRestController(
          final InboundConnectorRegistry registry,
          final ZeebeClient zeebeClient,
          final FeelEngineWrapper feelEngine,
          final ObjectMapper jsonMapper) {
    this.registry = registry;
    this.zeebeClient = zeebeClient;
    this.feelEngine = feelEngine;
    this.jsonMapper = jsonMapper;
  }

  @PostMapping("/inbound/{context}")
  public ResponseEntity<ProcessInstanceEvent> inbound(
      @PathVariable String context,
      @RequestBody byte[] bodyAsByteArray, // it is important to get pure body in order to recalculate HMAC
      @RequestHeader Map<String, String> headers) throws IOException {

    LOG.debug("Received inbound hook on {}", context);

    if (!registry.containsContextPath(context)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No webhook found for context: " + context);
    }
    WebhookConnectorProperties connectorProperties = registry.getWebhookConnectorByContextPath(context);

    // TODO(nikku): what context do we expose?
    // TODO(igpetrov): handling exceptions? Throw or fail? Maybe spring controller advice?
    Map bodyAsMap = jsonMapper.readValue(bodyAsByteArray, Map.class);
    final Map<String, Object> webhookContext =
        Map.of(
            "request",
            Map.of(
                "body", bodyAsMap,
                "headers", headers));

    final var valid = validateSecret(connectorProperties, webhookContext);

    if (!valid) {
      LOG.debug("Failed validation {} :: {} {}", context, webhookContext);
      return ResponseEntity.status(400).build();
    }

    try {
      // TODO(igpetrov): currently in test mode. Don't enforce for now.
      final var isHmacValid = isValidHmac(connectorProperties, bodyAsByteArray, headers);
      LOG.debug("Test mode: validating HMAC. Was {}", isHmacValid);
    } catch (NoSuchAlgorithmException e) {
      LOG.error("Wasn't able to recognise HMAC algorithm {}", connectorProperties.getHMACAlgo());
    } catch (InvalidKeyException e) {
      // FIXME: remove exposure of secret key when prototyping complit
      LOG.error("Secret key '{}' was invalid", connectorProperties.getHMACSecret());
    }

    final var shouldActivate = checkActivation(connectorProperties, webhookContext);
    if (!shouldActivate) {
      LOG.debug("Should not activate {} :: {}", context, webhookContext);
      return ResponseEntity.status(HttpStatus.OK).build();
    }

    final var variables = extractVariables(connectorProperties, webhookContext);

    final var processInstanceEvent = startInstance(connectorProperties, variables);

    LOG.debug(
        "Webhook {} created process instance {} with variables {}",
        connectorProperties,
        processInstanceEvent.getProcessInstanceKey(),
        variables);

    // TODO: how much context do we want to expose?

    // respond with 201 if execution triggered behavior
    return ResponseEntity.status(HttpStatus.CREATED).body(processInstanceEvent);
  }

  private boolean isValidHmac(final WebhookConnectorProperties connectorProperties,
                              final byte[] bodyAsByteArray,
                              final Map<String, String> headers)
          throws NoSuchAlgorithmException, InvalidKeyException {
    if ("disabled".equals(connectorProperties.shouldValidateHMAC())) {
      return true;
    }

    HMACSignatureValidator validator = new HMACSignatureValidator(
            bodyAsByteArray,
            headers,
            connectorProperties.getHMACHeader(),
            connectorProperties.getHMACSecret(),
            HMACAlgoCustomerChoice.valueOf(connectorProperties.getHMACAlgo())
    );

    return validator.isRequestValid();
  }

  private ProcessInstanceEvent startInstance(
          WebhookConnectorProperties connectorProperties, Map<String, Object> variables) {
    try {
      return zeebeClient
          .newCreateInstanceCommand()
          .bpmnProcessId(connectorProperties.getBpmnProcessId())
          .version(connectorProperties.getVersion())
          .variables(variables)
          .send()
          .join();
    } catch (Exception exception) {
      throw fail("Failed to start process instance", connectorProperties, exception);
    }
  }

  private ResponseStatusException fail(
      String message, WebhookConnectorProperties connectorProperties, Exception exception) {
    LOG.error("Webhook {} failed to create process instance", connectorProperties, exception);
    return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message);
  }

  private Map<String, Object> extractVariables(
          WebhookConnectorProperties connectorProperties, Map<String, Object> context) {

    var variableMapping = connectorProperties.getVariableMapping();
    if (variableMapping == null) {
      return context;
    }
    try {
      return feelEngine.evaluate(variableMapping, context);
    } catch (Exception exception) {
      throw fail("Failed to extract variables", connectorProperties, exception);
    }
  }

  private boolean checkActivation(
          WebhookConnectorProperties connectorProperties, Map<String, Object> context) {

    // at this point we assume secrets exist / had been specified
    var activationCondition = connectorProperties.getActivationCondition();
    if (activationCondition == null) {
      return true;
    }
    try {
      Object shouldActivate = feelEngine.evaluate(activationCondition, context);
      return Boolean.TRUE.equals(shouldActivate);
    } catch (Exception exception) {
      throw fail("Failed to check activation", connectorProperties, exception);
    }
  }

  private boolean validateSecret(
          WebhookConnectorProperties connectorProperties, Map<String, Object> context) {

    // at this point we assume secrets exist / had been specified
    var secretExtractor = connectorProperties.getSecretExtractor();
    var secret = connectorProperties.getSecret();
    try {
      String providedSecret = feelEngine.evaluate(secretExtractor, context);
      return secret.equals(providedSecret);
    } catch (Exception exception) {
      throw fail("Failed to validate secret", connectorProperties, exception);
    }
  }
}
