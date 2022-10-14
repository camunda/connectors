package io.camunda.connector.inbound.connector;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ConnectorController {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectorController.class);

  private ConnectorService connectorService;

  private ZeebeClient zeebeClient;

  private FeelEngineWrapper feelEngine;

  @Autowired
  public ConnectorController(
      ConnectorService connectorService, ZeebeClient zeebeClient, FeelEngineWrapper feelEngine) {
    this.connectorService = connectorService;
    this.zeebeClient = zeebeClient;
    this.feelEngine = feelEngine;
  }

  @PostMapping("/inbound/{context}")
  public ResponseEntity<ProcessInstanceEvent> inbound(
      @PathVariable String context,
      @RequestBody Map<String, Object> body,
      @RequestHeader Map<String, String> headers) {

    LOG.debug("Received inbound hook on {}", context);

    final var connectorProperties =
        connectorService
            .get(context)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No webhook found for context: " + context));

    // TODO(nikku): what context do we expose?
    final Map<String, Object> webhookContext =
        Map.of(
            "request",
            Map.of(
                "body", body,
                "headers", headers));

    final var valid = validateSecret(connectorProperties, webhookContext);

    if (!valid) {
      LOG.debug("Failed validation {} :: {} {}", context, webhookContext);

      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
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

    // TODO(nikku): how much context do we want to expose?

    // respond with 201 if execution triggered behavior
    return ResponseEntity.status(HttpStatus.CREATED).body(processInstanceEvent);
  }

  private ProcessInstanceEvent startInstance(
      ConnectorProperties connectorProperties, Map<String, Object> variables) {

    try {
      return zeebeClient
          .newCreateInstanceCommand()
          .bpmnProcessId(connectorProperties.bpmnProcessId())
          .version(connectorProperties.version())
          .variables(variables)
          .send()
          .join();
    } catch (Exception exception) {
      throw fail("Failed to start process instance", connectorProperties, exception);
    }
  }

  private ResponseStatusException fail(
      String message, ConnectorProperties connectorProperties, Exception exception) {

    LOG.error("Webhook {} failed to create process instance", connectorProperties, exception);

    return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message);
  }

  private Map<String, Object> extractVariables(
      ConnectorProperties connectorProperties, Map<String, Object> context) {

    var variableMapping = connectorProperties.variableMapping();

    if (variableMapping == null) {
      return context;
    }

    try {
      Map<String, Object> variables = feelEngine.evaluate(variableMapping, context);

      return variables;
    } catch (Exception exception) {
      throw fail("Failed to extract variables", connectorProperties, exception);
    }
  }

  private boolean checkActivation(
      ConnectorProperties connectorProperties, Map<String, Object> context) {

    // at this point we assume secrets exist / had been specified
    var activationCondition = connectorProperties.activationCondition();

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
      ConnectorProperties connectorProperties, Map<String, Object> context) {

    // at this point we assume secrets exist / had been specified
    var secretExtractor = connectorProperties.secretExtractor();
    var secret = connectorProperties.secret();

    try {
      String providedSecret = feelEngine.evaluate(secretExtractor, context);

      return secret.equals(providedSecret);
    } catch (Exception exception) {
      throw fail("Failed to validate secret", connectorProperties, exception);
    }
  }
}
