/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.core.document;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentContent.TransientDocumentContent;
import java.util.Map;
import java.util.stream.Collectors;

public class DocumentUtil {

  @SuppressWarnings("unchecked")
  public static Map<String, Object> replaceTransientDocumentsWithStatic(
      DocumentFactory documentFactory, Map<String, Object> extractedVariables) {

    return extractedVariables.entrySet().stream()
        .map(
            entry -> {
              Object value = entry.getValue();
              if (value instanceof Map<?, ?>) {
                Map<String, Object> map = (Map<String, Object>) value;
                return Map.entry(
                    entry.getKey(), replaceTransientDocumentsWithStatic(documentFactory, map));
              }
              if (value instanceof Document document) {
                var content = document.getContent();
                if (content instanceof TransientDocumentContent) {
                  return Map.entry(
                      entry.getKey(), documentFactory.transformTransientDocumentToStatic(document));
                }
              }
              return entry;
            })
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
