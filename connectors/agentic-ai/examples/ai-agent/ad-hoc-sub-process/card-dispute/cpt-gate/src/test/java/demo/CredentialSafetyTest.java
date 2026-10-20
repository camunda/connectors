package demo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CredentialSafetyTest {
  @Test void referencesAreAcceptedAndRawKeysRejectedWithoutPrintingThem() {
    var safe = Demo.JSON.valueToTree(Map.of("kind", "SECRET_REFERENCE", "value", Map.of(
        "endpoint", "camunda.secrets.FAKE_ENDPOINT", "apiKey", "camunda.secrets.FAKE_API_KEY", "model", "camunda.secrets.FAKE_MODEL")));
    assertDoesNotThrow(() -> CredentialSafety.credential(safe));
    assertDoesNotThrow(() -> CredentialSafety.noRawKeys(safe));
    assertThrows(AssertionError.class, () -> CredentialSafety.noRawKeys(Demo.JSON.valueToTree(
        Map.of("apiKey", "synthetic-not-a-reference"))));
    assertThrows(AssertionError.class, () -> CredentialSafety.noRawKeys(Demo.JSON.valueToTree(
        "sk-" + "synthetic-key-for-unit-test-only")));
    assertThrows(AssertionError.class, () -> CredentialSafety.credential(Demo.JSON.valueToTree(
        Map.of("kind", "JSON", "value", Map.of()))));
    var dummy = Demo.JSON.valueToTree(Map.of("apiKey", DaytonaEndpoint.FAKE_KEY, "apiUrl", "http://localhost:1234/api"));
    assertThrows(AssertionError.class, () -> CredentialSafety.noRawKeys(dummy));
    assertThrows(AssertionError.class, () -> CredentialSafety.noRawKeys(dummy, "http://localhost:5678/api"));
    assertDoesNotThrow(() -> CredentialSafety.noRawKeys(dummy, "http://localhost:1234/api"));
  }
}
