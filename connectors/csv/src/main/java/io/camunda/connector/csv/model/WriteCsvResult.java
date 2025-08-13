/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.csv.model;

public sealed interface WriteCsvResult permits WriteCsvResult.Document, WriteCsvResult.Value {

  record Document(io.camunda.connector.api.document.Document document) implements WriteCsvResult {}

  record Value(String content) implements WriteCsvResult {}
}
