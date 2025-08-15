/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box.model;

import io.camunda.connector.api.document.Document;
import java.util.List;

public sealed interface BoxResult
    permits BoxResult.Download, BoxResult.Generic, BoxResult.Search, BoxResult.Upload {
  record Item(String id, String type) {}

  record Download(Item item, Document document) implements BoxResult {}

  record Upload(Item item) implements BoxResult {}

  record Generic(Item item) implements BoxResult {}

  record Search(List<Item> items) implements BoxResult {}
}
