/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.feel;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElementParameter;
import java.util.List;

/**
 * Extracts parameters defined as tagging functions (such as fromAi) from a given FEEL expression
 * string.
 */
public interface FeelExpressionParameterExtractor {
  List<AdHocToolElementParameter> extractParameters(String expression);
}
