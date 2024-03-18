/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import io.camunda.connectors.soap.client.SoapClient;
import io.camunda.connectors.soap.client.SpringSoapClient;
import io.camunda.connectors.soap.message.SoapMessageHandler;
import io.camunda.connectors.soap.xml.Mapper;
import io.camunda.connectors.soap.xml.XmlUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.FaultAwareWebServiceMessage;
import org.springframework.ws.WebServiceException;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.client.SoapFaultClientException;

@OutboundConnector(
    name = "SOAP Connector",
    type = SoapConnector.SOAP_CONNECTOR_TYPE,
    inputVariables = {
      "serviceUrl",
      "authentication",
      "soapVersion",
      "header",
      "body",
      "namespaces",
      "connectionTimeoutInSeconds"
    })
@ElementTemplate(
    id = "io.camunda:soap",
    name = "SOAP Connector",
    icon = "icon.svg",
    version = 1,
    inputDataClass = SoapConnectorInput.class,
    description = "A Connector to execute a SOAP request",
    propertyGroups = {
      @PropertyGroup(id = "connection", label = "Connection"),
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "soap-message", label = "SOAP Message"),
      @PropertyGroup(id = "timeout", label = "Timeout")
    })
public class SoapConnector implements OutboundConnectorFunction {
  public static final String SOAP_CONNECTOR_TYPE = "io.camunda:soap:1";
  private static final Logger LOG = LoggerFactory.getLogger(SoapConnector.class);
  private final SoapMessageHandler soapMessageHandler;
  private final SoapClient soapClient;

  public SoapConnector() {
    this(new SoapMessageHandler(), new SpringSoapClient());
  }

  public SoapConnector(SoapMessageHandler soapMessageHandler, SoapClient soapClient) {
    this.soapMessageHandler = soapMessageHandler;
    this.soapClient = soapClient;
  }

  protected SoapConnectorInput getInput(OutboundConnectorContext context) {
    return context.bindVariables(SoapConnectorInput.class);
  }

  protected Object handleResult(JsonNode response) {
    return response;
  }

  protected Exception handleException(SoapConnectorException e) {
    return e;
  }

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    SoapConnectorInput input = getInput(context);
    String soapBody = soapMessageHandler.generateBody(input.body());
    String soapHeader = soapMessageHandler.generateHeader(input.header());
    try {
      String soapResponseMessage =
          soapClient.sendSoapRequest(
              input.serviceUrl(),
              input.soapVersion(),
              soapHeader,
              soapBody,
              input.authentication(),
              input.connectionTimeoutInSeconds(),
              input.namespaces());
      JsonNode response =
          Mapper.toJson()
              .withPreserveNamespaces(false)
              .build()
              .toJson(XmlUtilities.xmlStringToDocument(soapResponseMessage));
      LOG.debug("Response to connector runtime: \n{}", response.toPrettyString());
      return handleResult(response);
    } catch (WebServiceException e) {
      if (e instanceof SoapFaultClientException soapFaultClientException) {
        FaultAwareWebServiceMessage webServiceMessage =
            soapFaultClientException.getWebServiceMessage();
        if (webServiceMessage instanceof SoapMessage soapMessage) {
          throw handleException(
              new SoapConnectorException(
                  e,
                  Mapper.toJson()
                      .withPreserveNamespaces(false)
                      .build()
                      .toJson(soapMessage.getDocument())));
        }
      }
      throw e;
    }
  }
}
