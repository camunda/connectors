package demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillInputsTest {
  @TempDir Path demo;

  private Map<String, String> prepare() throws Exception {
    Path fixtures = Path.of("src/test/resources/round3/skills/chargeback-deadline");
    for (String relative : SkillInputs.PATHS) {
      Path target = demo.resolve("skills/chargeback-deadline").resolve(relative);
      Files.createDirectories(target.getParent());
      Files.copy(fixtures.resolve(relative), target);
    }
    return SkillInputs.manifest(demo.resolve("skills/chargeback-deadline"));
  }

  private com.fasterxml.jackson.databind.JsonNode uploaded(Path file) {
    return Demo.JSON.valueToTree(Map.of("documentId", "uploaded-" + file.getFileName(),
        "metadata", Map.of("fileName", file.getFileName().toString())));
  }

  @Test void fiveDocumentInputsUseOriginalHashesAndExplicitFilingDate() throws Exception {
    var manifest = prepare();
    var inputs = SkillInputs.upload(demo, manifest, "2026-10-20", (path, type) -> uploaded(path));
    assertThat(inputs.get("deadlineSkillManifest")).isEqualTo(manifest);
    assertThat(inputs.get("deadlineScriptSha256")).isEqualTo(SkillInputs.SCRIPT_SHA);
    assertThat(inputs.get("disputeFilingDate")).isEqualTo("2026-10-20");
    assertThat(Demo.JSON.valueToTree(inputs.get("disputeDocuments")).size()).isEqualTo(5);
  }

  @Test void missingOrChangedBytesFailBeforeUploads() throws Exception {
    var manifest = prepare();
    var count = new AtomicInteger();
    Path reference = demo.resolve("skills/chargeback-deadline/references/network-rules.md");
    Files.writeString(reference, "changed");
    assertThatThrownBy(() -> SkillInputs.upload(demo, manifest, "2026-10-20",
        (path, type) -> { count.incrementAndGet(); return uploaded(path); }))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("manifest");
    Files.delete(reference);
    assertThatThrownBy(() -> SkillInputs.upload(demo, manifest, "2026-10-20",
        (path, type) -> { count.incrementAndGet(); return uploaded(path); }))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Missing skill document");
    assertThat(count.get()).isZero();
  }

  @Test void uploadIdentityAndConcurrentByteChangeFailClosed() throws Exception {
    var manifest = prepare();
    assertThatThrownBy(() -> SkillInputs.upload(demo, manifest, "2026-10-20",
        (path, type) -> Demo.JSON.valueToTree(Map.of("documentId", "same-id"))))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("uploaded document");
    assertThatThrownBy(() -> SkillInputs.upload(demo, manifest, "2026-10-20",
        (path, type) -> {
          if (path.getFileName().toString().equals("deadline.py")) {
            Files.writeString(demo.resolve("skills/chargeback-deadline/SKILL.md"), "changed during upload");
          }
          return uploaded(path);
        })).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("changed during upload");
  }
}
