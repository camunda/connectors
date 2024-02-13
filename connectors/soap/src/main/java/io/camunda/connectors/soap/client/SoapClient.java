/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap.client;

import io.camunda.connectors.soap.SoapConnectorInput.Authentication;
import io.camunda.connectors.soap.SoapConnectorInput.Version;
import java.util.Map;

public interface SoapClient {

  String sendSoapRequest(
      String serviceUrl,
      Version soapVersion,
      String soapHeader,
      String soapBody,
      Authentication authentication,
      Integer connectionTimeoutInSeconds,
      Map<String, String> namespaces)
      throws Exception;
}
