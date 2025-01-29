/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.javascript;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.javascript.model.JavascriptInputRequest;
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

  final static String JS_MAGIC_ENDPOINT = "http://ec2-16-171-176-240.eu-north-1.compute.amazonaws.com:8000";

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    System.out.println("Executing Javascript connector");
    var input = context.bindVariables(JavascriptInputRequest.class);
    try (final var client = HttpClient.newHttpClient()) {
      // Execute the Javascript code
      final var request = HttpRequest.newBuilder()
          .uri(URI.create(JS_MAGIC_ENDPOINT))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(
              "{\"script\": \"" + input.script() + "\", \"parameters\": " + input.parameters()
                  + "}"));
      final var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
      return response.body();
    }
  }
}
