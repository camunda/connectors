package io.camunda.connector.inbound.connector;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ConnectorController {

  @Autowired
  private ConnectorService connectorService;

  @Autowired
  private ZeebeClient zeebeClient;

  @PostMapping("/inbound/{context}")
  public ResponseEntity<ProcessInstanceEvent> inbound(@PathVariable String context,
      @RequestBody Map<String, Object> body) {
    final ConnectorProperties connectorProperties = connectorService.get(context)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
          "No webhook found for context: " + context));

    final ProcessInstanceEvent event = zeebeClient.newCreateInstanceCommand()
        .bpmnProcessId(connectorProperties.bpmnProcessId())
        .version(connectorProperties.version())
        .variables(body)
        .send().join();

    return ResponseEntity.ok(event);
  }

}
