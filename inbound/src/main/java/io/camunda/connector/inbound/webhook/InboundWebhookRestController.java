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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
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

  @Autowired
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
  public ResponseEntity<WebhookResponse> inbound(
      @PathVariable String context,
      @RequestBody byte[] bodyAsByteArray, // it is important to get pure body in order to recalculate HMAC
      @RequestHeader Map<String, String> headers) throws IOException {

    LOG.debug("Received inbound hook on {}", context);

    if (!registry.containsContextPath(context)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No webhook found for context: " + context);
    }

    // TODO(nikku): what context do we expose?
    // TODO(igpetrov): handling exceptions? Throw or fail? Maybe spring controller advice?
    // TODO: Check if that always works (can we have an empty body for example?)
    Map bodyAsMap = jsonMapper.readValue(bodyAsByteArray, Map.class);
    final Map<String, Object> webhookContext = Map.of(
            "request", Map.of(
                "body", bodyAsMap,
                "headers", headers));

    WebhookResponse response = new WebhookResponse();
    Collection<WebhookConnectorProperties> connectors = registry.getWebhookConnectorByContextPath(context);
    for (WebhookConnectorProperties connectorProperties : connectors) {

      try {
        if (!validateSecret(connectorProperties, webhookContext)) {
          LOG.debug("Failed validation {} :: {} {}", context, webhookContext);
          response.addUnauthorizedConnector(connectorProperties);
        } else { // Authorized

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

          if (!checkActivation(connectorProperties, webhookContext)) {
            LOG.debug("Should not activate {} :: {}", context, webhookContext);
            response.addUnactivatedConnector(connectorProperties);
          } else {
            ProcessInstanceEvent processInstanceEvent = executeWebhookConnector(connectorProperties, webhookContext);
            LOG.debug("Webhook {} created process instance {}", connectorProperties, processInstanceEvent);
            response.addExecutedConnector(connectorProperties, processInstanceEvent);
          }
        }
      } catch (Exception exception) {
        LOG.error("Webhook {} failed to create process instance", connectorProperties, exception);
        response.addException(connectorProperties, exception);
      }
    }

    return ResponseEntity.status(HttpStatus.OK).body(response);
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

  /**
   * This could be potentially moved to an interface?
   * See https://github.com/camunda/connector-sdk-inbound-webhook/issues/26
   * @return
   */
  private ProcessInstanceEvent executeWebhookConnector(WebhookConnectorProperties connectorProperties, Map<String, Object> webhookContext) {
    final Map<String, Object> variables = extractVariables(connectorProperties, webhookContext);

    return zeebeClient
        .newCreateInstanceCommand()
        .bpmnProcessId(connectorProperties.getBpmnProcessId())
        .version(connectorProperties.getVersion())
        .variables(variables)
        .send()
        .join();
      //throw fail("Failed to start process instance", connectorProperties, exception);
  }

  private Map<String, Object> extractVariables(
          WebhookConnectorProperties connectorProperties, Map<String, Object> context) {

    var variableMapping = connectorProperties.getVariableMapping();
    if (variableMapping == null) {
      return context;
    }
    return feelEngine.evaluate(variableMapping, context);
//      throw fail("Failed to extract variables", connectorProperties, exception);
  }

  private boolean checkActivation(
          WebhookConnectorProperties connectorProperties, Map<String, Object> context) {

    // at this point we assume secrets exist / had been specified
    var activationCondition = connectorProperties.getActivationCondition();
    if (activationCondition == null) {
      return true;
    }
    Object shouldActivate = feelEngine.evaluate(activationCondition, context);
    return Boolean.TRUE.equals(shouldActivate);
//      throw fail("Failed to check activation", connectorProperties, exception);
  }

  private boolean validateSecret(
          WebhookConnectorProperties connectorProperties, Map<String, Object> context) {

    // at this point we assume secrets exist / had been specified
    var secretExtractor = connectorProperties.getSecretExtractor();
    var secret = connectorProperties.getSecret();

    String providedSecret = feelEngine.evaluate(secretExtractor, context);
    return secret.equals(providedSecret);
//      throw fail("Failed to validate secret", connectorProperties, exception);

  }
}
