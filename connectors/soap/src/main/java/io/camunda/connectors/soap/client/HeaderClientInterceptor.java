/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap.client;

import io.camunda.connectors.soap.xml.XmlUtilities;
import java.util.Map;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.support.interceptor.ClientInterceptorAdapter;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapMessage;

public class HeaderClientInterceptor extends ClientInterceptorAdapter {
  private final String soapHeader;
  private final Map<String, String> namespaces;

  public HeaderClientInterceptor(String soapHeader, Map<String, String> namespaces) {
    this.soapHeader = soapHeader;
    this.namespaces = namespaces;
  }

  @Override
  public boolean handleRequest(MessageContext messageContext) throws WebServiceClientException {
    if (messageContext.getRequest() instanceof SoapMessage soapMessage) {
      Source source = soapMessage.getSoapHeader().getSource();
      if (source instanceof DOMSource domSource) {
        XmlUtilities.appendXmlStringToNode(soapHeader, namespaces, domSource.getNode());
      }
    }
    return true;
  }
}
