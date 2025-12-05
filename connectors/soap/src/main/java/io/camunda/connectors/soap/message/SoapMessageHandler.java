/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap.message;

import io.camunda.connectors.soap.SoapConnectorInput.SoapBodyPart;
import io.camunda.connectors.soap.SoapConnectorInput.SoapBodyPart.BodyJson;
import io.camunda.connectors.soap.SoapConnectorInput.SoapBodyPart.BodyTemplate;
import io.camunda.connectors.soap.SoapConnectorInput.SoapHeaderPart;
import io.camunda.connectors.soap.SoapConnectorInput.SoapHeaderPart.HeaderJson;
import io.camunda.connectors.soap.SoapConnectorInput.SoapHeaderPart.HeaderNone;
import io.camunda.connectors.soap.SoapConnectorInput.SoapHeaderPart.HeaderTemplate;
import io.camunda.connectors.soap.xml.Mapper;
import io.camunda.connectors.soap.xml.TemplateUtil;
import io.camunda.connectors.soap.xml.ToXmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoapMessageHandler {
  private static final Logger LOG = LoggerFactory.getLogger(SoapMessageHandler.class);
  private final ToXmlMapper toXmlMapper;

  public SoapMessageHandler() {
    this(Mapper.toXml().build());
  }

  public SoapMessageHandler(ToXmlMapper toXmlMapper) {
    this.toXmlMapper = toXmlMapper;
  }

  public String generateBody(SoapBodyPart soapBodyPart) {
    return processSoapBodyPart(soapBodyPart);
  }

  public String generateHeader(SoapHeaderPart soapHeaderPart) {
    return processSoapHeaderPart(soapHeaderPart);
  }

  private String processSoapHeaderPart(SoapHeaderPart xmlPart) {
    if (xmlPart == null || xmlPart instanceof HeaderNone) {
      LOG.trace("No header provided for SOAP message.");
      return null;
    } else if (xmlPart instanceof HeaderTemplate template) {
      String soapHeader =
          TemplateUtil.compileTemplate(template.template(), true).execute(template.context());
      LOG.trace("Generated SOAP Header from XML template: {}", soapHeader);
      return soapHeader;
    } else if (xmlPart instanceof HeaderJson json) {
      String soapHeader = toXmlMapper.toXmlPartString(json.json());
      LOG.trace("Generated SOAP Header from JSON template: {}", soapHeader);
      return soapHeader;
    } else {
      throw new IllegalStateException("Unrecognized SOAP header implementation");
    }
  }

  private String processSoapBodyPart(SoapBodyPart xmlPart) {
    if (xmlPart == null) {
      LOG.trace("No body provided for SOAP message.");
      return "";
    } else if (xmlPart instanceof BodyTemplate template) {
      String soapBody =
          TemplateUtil.compileTemplate(template.template(), true).execute(template.context());
      LOG.trace("Generated SOAP Body from XML template: {}", soapBody);
      return soapBody;
    } else if (xmlPart instanceof BodyJson json) {
      String soapBody = toXmlMapper.toXmlPartString(json.json());
      LOG.trace("Generated SOAP Body from JSON template: {}", soapBody);
      return soapBody;
    } else {
      throw new IllegalStateException("Unrecognized SOAP body implementation");
    }
  }
}
