/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap.client;

import io.camunda.connectors.soap.xml.XmlUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapMessage;

public class LoggingInterceptor implements ClientInterceptor {
  private static final Logger LOG = LoggerFactory.getLogger(LoggingInterceptor.class);

  @Override
  public boolean handleRequest(MessageContext messageContext) throws WebServiceClientException {
    if (messageContext.getRequest() instanceof SoapMessage soapMessage) {
      String document = XmlUtilities.xmlDocumentToString(soapMessage.getDocument(), false, true);
      LOG.debug("Request: \n{}", document);
    }
    return true;
  }

  @Override
  public boolean handleResponse(MessageContext messageContext) throws WebServiceClientException {
    if (messageContext.getResponse() instanceof SoapMessage soapMessage) {
      String document = XmlUtilities.xmlDocumentToString(soapMessage.getDocument(), false, true);
      LOG.debug("Response: \n{}", document);
    }
    return true;
  }

  @Override
  public boolean handleFault(MessageContext messageContext) throws WebServiceClientException {
    return true;
  }

  @Override
  public void afterCompletion(MessageContext messageContext, Exception ex)
      throws WebServiceClientException {}
}
