/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.csv.model;

import java.util.List;
import java.util.Map;

public sealed interface ReadCsvResult permits ReadCsvResult.Objects, ReadCsvResult.Arrays {

  record Objects(List<Map<String, String>> records) implements ReadCsvResult {}

  record Arrays(List<List<String>> records) implements ReadCsvResult {}
}
