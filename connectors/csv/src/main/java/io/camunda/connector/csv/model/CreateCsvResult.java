/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.csv.model;

public sealed interface CreateCsvResult permits CreateCsvResult.Document, CreateCsvResult.Value {

  record Document(io.camunda.document.Document document) implements CreateCsvResult {}

  record Value(String csv) implements CreateCsvResult {}
}
