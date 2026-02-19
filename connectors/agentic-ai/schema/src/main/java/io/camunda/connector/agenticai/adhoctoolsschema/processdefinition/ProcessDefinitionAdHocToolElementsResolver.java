/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.processdefinition;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import java.util.List;

/**
 * Resolves the tool elements for ad-hoc tools based on the process definition key and ad-hoc
 * subprocess ID. Loads the process definition and extracts the tool elements and their parameters
 * from the definition XML.
 */
public interface ProcessDefinitionAdHocToolElementsResolver {
  List<AdHocToolElement> resolveToolElements(Long processDefinitionKey, String adHocSubProcessId);
}
