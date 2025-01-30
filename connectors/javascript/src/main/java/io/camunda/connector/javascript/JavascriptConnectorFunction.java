/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.javascript;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.javascript.model.DenoRequest;
import io.camunda.connector.javascript.model.JavascriptInputRequest;
import io.camunda.document.Document;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@OutboundConnector(
    name = "Javascript Connector",
    inputVariables = {"script", "parameters"},
    type = "io.camunda:javascript:1")
@ElementTemplate(
    id = "io.camunda.connectors.javascript.v1",
    name = "Javascript Connector",
    description = "Execute custom Javascript connectors",
    inputDataClass = JavascriptInputRequest.class,
    version = 1,
    propertyGroups = {@ElementTemplate.PropertyGroup(id = "javascript", label = "JavaScript")},
    documentationRef = "",
    icon = "icon.svg")
public class JavascriptConnectorFunction implements OutboundConnectorFunction {

  static final String JS_MAGIC_ENDPOINT =
      "http://ec2-16-171-176-240.eu-north-1.compute.amazonaws.com:8000/execute";
  static final ObjectMapper MAPPER = ConnectorsObjectMapperSupplier.getCopy();

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    System.out.println("Executing Javascript connector");
    var input = context.bindVariables(JavascriptInputRequest.class);

    String script = null;
    if (input.script() instanceof Document document) {
      script = new String(document.asByteArray());
    } else if (input.script() instanceof String) {
      script = (String) input.script();
    } else {
      throw new IllegalArgumentException("Unsupported script type: " + input.script().getClass());
    }

    final var denoRequest = new DenoRequest(script, input.parameters());
    System.out.println(denoRequest);

    try (final var client = HttpClient.newHttpClient()) {

      final var request =
          HttpRequest.newBuilder()
              .uri(URI.create(JS_MAGIC_ENDPOINT))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(denoRequest)));

      final var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
      System.out.println(response.body());
      return response.body();
    }
  }
}
