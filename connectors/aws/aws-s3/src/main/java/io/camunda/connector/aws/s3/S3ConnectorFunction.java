package io.camunda.connector.aws.s3;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.s3.model.S3Request;
import io.camunda.connector.generator.java.annotation.ElementTemplate;

@OutboundConnector(
    name = "AWS S3",
    inputVariables = {"authentication", "configuration"},
    type = "io.camunda:aws-s3:1")
@ElementTemplate(
    id = "io.camunda.connectors.aws.s3.v1",
    name = "AWS S3 Outbound Connector",
    description = "Execute S3 requests",
    inputDataClass = S3Request.class,
    version = 1,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Configuration"),
    },
    documentationRef = "https://docs.camunda.io/docs/",
    icon = "icon.svg")
public class S3ConnectorFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    return null;
  }
}
