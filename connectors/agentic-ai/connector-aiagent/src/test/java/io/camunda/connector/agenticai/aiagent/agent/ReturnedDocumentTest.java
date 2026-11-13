/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

class ReturnedDocumentTest {

  @Test
  void writesTheDocumentAsItsCamundaDocumentReferenceAndReadsItBack() throws Exception {
    final var reference = TestDocuments.reference("report", "report.pdf");
    final var returned = new ReturnedDocument(reference, "The report");
    final var mapper = TestObjectMapperSupplier.INSTANCE;

    final var json = mapper.writeValueAsString(returned);

    JSONAssert.assertEquals(
        """
        {
          "document": {
            "camunda.document.type": "camunda",
            "storeId": "in-memory",
            "documentId": "%s",
            "contentHash": "%s",
            "metadata": {"contentType": "text/plain", "fileName": "report.pdf"}
          },
          "description": "The report"
        }
        """
            .formatted(reference.documentId(), reference.contentHash()),
        json,
        JSONCompareMode.STRICT);
    assertThat(mapper.readValue(json, ReturnedDocument.class)).isEqualTo(returned);
  }

  @Test
  void leavesOutAMissingDescription() throws Exception {
    final var json =
        TestObjectMapperSupplier.INSTANCE.writeValueAsString(
            new ReturnedDocument(TestDocuments.reference("report", "report.pdf"), null));

    assertThat(json).doesNotContain("description");
  }
}
