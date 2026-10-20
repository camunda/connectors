package demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SandboxCleanupTest {
  static final String ONE = "11111111-1111-4111-8111-111111111111";
  static final String TWO = "22222222-2222-4222-8222-222222222222";

  @Test void budgetFailureStillDeletesEveryOwnedSandboxAndVerifiesAbsence() throws Exception {
    var remaining = new ArrayList<>(List.of(ONE, TWO));
    var deleted = new ArrayList<String>();
    var cleanup = new SandboxCleanup((method, url, query) -> {
      if (method.equals("DELETE")) {
        String id = url.substring(url.lastIndexOf('/') + 1);
        deleted.add(id);
        remaining.remove(id);
        return Demo.JSON.valueToTree(Map.of("deleted", true));
      }
      assertThat(query.get("labels")).isEqualTo("{\"processInstanceKey\":\"123\"}");
      return Demo.JSON.valueToTree(Map.of("items", remaining.stream()
          .map(id -> Map.of("id", id, "owner", "123")).toList()));
    });
    assertThatThrownBy(() -> cleanup.allocation(123, ONE)).isInstanceOf(AssertionError.class)
        .hasMessageContaining("exactly one");
    cleanup.deleteOwned(123);
    assertThat(deleted).containsExactly(ONE, TWO);
    assertThat(remaining).isEmpty();
  }

  @Test void foreignSandboxIsNeverDeleted() {
    var cleanup = new SandboxCleanup((method, url, query) -> {
      assertThat(method).isEqualTo("GET");
      return Demo.JSON.valueToTree(Map.of("items", List.of(Map.of("id", ONE, "owner", "foreign"))));
    });
    assertThatThrownBy(() -> cleanup.deleteOwned(123)).isInstanceOf(AssertionError.class)
        .hasMessageContaining("foreign");
  }

  @Test void failedDeletionDoesNotSkipOtherOwnedSandboxes() {
    var deleted = new ArrayList<String>();
    var cleanup = new SandboxCleanup((method, url, query) -> {
      if (method.equals("GET")) return Demo.JSON.valueToTree(Map.of("items",
          List.of(Map.of("id", ONE, "owner", "123"), Map.of("id", TWO, "owner", "123"))));
      deleted.add(url.substring(url.lastIndexOf('/') + 1));
      throw new IOException("Synthetic deletion failure");
    });
    assertThatThrownBy(() -> cleanup.deleteOwned(123)).isInstanceOf(Exception.class)
        .hasMessage("Owned sandbox deletion failed");
    assertThat(deleted).containsExactly(ONE, TWO);
  }
}
