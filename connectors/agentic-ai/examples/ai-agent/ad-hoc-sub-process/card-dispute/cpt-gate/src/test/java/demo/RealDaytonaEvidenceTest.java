package demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RealDaytonaEvidenceTest {
  @Test void importedCsvIsJoinedByDocumentIdWhenToolReferenceOmitsMetadata() throws Exception {
    var call = Demo.JSON.readTree("""
        {"operation":"IMPORT_DOCUMENT","document":{"documentId":"csv-upload","metadata":null}}
        """);
    var result = Demo.JSON.getNodeFactory().textNode("Imported 'transactions-2026-09.csv' (1598 bytes) to /home/daytona/transactions.csv.");
    assertThat(RealDaytonaEvidence.importedCsvId(call, result, Set.of("csv-upload"))).isEqualTo("csv-upload");
    assertThat(RealDaytonaEvidence.importedCsvId(call, result, Set.of("different-upload"))).isEmpty();
    assertThat(RealDaytonaEvidence.csvImportIdentity(Map.of("scope", call),
        Map.of("scope", result), Set.of("csv-upload"))).isEqualTo("csv-upload");
  }

  @Test void failedImportCannotCertifyTheUploadedCsv() throws Exception {
    var call = Demo.JSON.readTree("""
        {"operation":"IMPORT_DOCUMENT","document":{"documentId":"csv-upload","metadata":null}}
        """);
    assertThatThrownBy(() -> RealDaytonaEvidence.importedCsvId(call,
        Demo.JSON.getNodeFactory().textNode("Import failed"), Set.of("csv-upload")))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(() -> RealDaytonaEvidence.importedCsvId(call,
        Demo.JSON.readTree("{\"error\":\"failed\"}"), Set.of("csv-upload")))
        .isInstanceOf(AssertionError.class);
  }

  @Test void missingImportIdentityNamesTheExpectedFieldAndSource() throws Exception {
    var call = Demo.JSON.readTree("""
        {"operation":"IMPORT_DOCUMENT","document":{"metadata":null}}
        """);
    var result = Demo.JSON.getNodeFactory().textNode("Imported 'transactions.csv' (1598 bytes) to transactions.csv.");
    assertThatThrownBy(() -> RealDaytonaEvidence.csvImportIdentity(Map.of("scope", call),
        Map.of("scope", result), Set.of("csv-upload")))
        .isInstanceOf(AssertionError.class).hasMessageContaining("IMPORT_DOCUMENT.document.documentId")
        .hasMessageContaining("disputeDocuments");
    assertThatThrownBy(() -> RealDaytonaEvidence.csvImportIdentity(Map.of(), Map.of(), Set.of()))
        .isInstanceOf(AssertionError.class).hasMessageContaining("disputeDocuments[metadata.fileName=transactions-2026-09.csv].documentId");
  }
}
