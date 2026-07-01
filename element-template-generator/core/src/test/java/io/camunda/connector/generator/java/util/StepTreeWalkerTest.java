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
package io.camunda.connector.generator.java.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.generator.dsl.GroupStep;
import io.camunda.connector.generator.dsl.LeafStep;
import io.camunda.connector.generator.dsl.Preset;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StepTreeWalkerTest {

  // ---------- One-level fixtures (S3-like) ----------

  @TemplateDiscriminatorProperty(name = "actionDiscriminator", group = "operation")
  sealed interface OneLevelRoot permits OneLevelUpload, OneLevelDownload {}

  @TemplateSubType(
      id = "uploadObject",
      label = "Upload object",
      description = "Upload an object",
      keywords = {"upload", "put"})
  record OneLevelUpload(String bucket) implements OneLevelRoot {}

  @TemplateSubType(
      id = "downloadObject",
      keywords = {"download", "get"})
  record OneLevelDownload(String bucket) implements OneLevelRoot {}

  // ---------- Two-level fixtures (DynamoDB-like) with `input` field prefix ----------

  static class TwoLevelInputWrapper {
    @SuppressWarnings("unused")
    TwoLevelRoot input;
  }

  @TemplateDiscriminatorProperty(name = "operationGroup", group = "operation")
  sealed interface TwoLevelRoot permits TwoLevelGroupTable, TwoLevelGroupItem {}

  @TemplateSubType(id = "tableOperation", label = "Table", description = "Manage tables")
  @TemplateDiscriminatorProperty(name = "tableOperation", group = "operation")
  sealed interface TwoLevelGroupTable extends TwoLevelRoot
      permits TwoLevelCreateTable, TwoLevelDeleteTable {}

  @TemplateSubType(
      id = "createTable",
      keywords = {"create table"})
  record TwoLevelCreateTable(String name) implements TwoLevelGroupTable {}

  @TemplateSubType(
      id = "deleteTable",
      keywords = {"delete table"})
  record TwoLevelDeleteTable(String name) implements TwoLevelGroupTable {}

  @TemplateSubType(id = "itemOperation", label = "Item", description = "Manage items")
  @TemplateDiscriminatorProperty(name = "itemOperation", group = "operation")
  sealed interface TwoLevelGroupItem extends TwoLevelRoot permits TwoLevelGetItem {}

  @TemplateSubType(
      id = "getItem",
      keywords = {"get item"})
  record TwoLevelGetItem(String key) implements TwoLevelGroupItem {}

  // ---------- One-level with @NestedProperties(addNestedPath = false) ----------

  static class NoPathWrapper {
    @NestedProperties(addNestedPath = false)
    @SuppressWarnings("unused")
    OneLevelRoot action;
  }

  // ---------- Operation root reachable only via another sealed type's permits ----------

  @TemplateDiscriminatorProperty(name = "authType", group = "authentication")
  sealed interface NestedAuth permits NestedAuthBasic, NestedAuthCarryingOps {}

  @TemplateSubType(id = "basic", label = "Basic")
  record NestedAuthBasic(String username) implements NestedAuth {}

  @TemplateSubType(id = "carrying", label = "Carrying")
  record NestedAuthCarryingOps(OneLevelRoot action) implements NestedAuth {}

  static class NestedAuthWrapper {
    @SuppressWarnings("unused")
    NestedAuth auth;
  }

  // ---------- Generic-container wrappers ----------

  static class ListWrapper {
    @SuppressWarnings("unused")
    java.util.List<OneLevelRoot> actions;
  }

  static class OptionalWrapper {
    @SuppressWarnings("unused")
    java.util.Optional<OneLevelRoot> action;
  }

  static class MapWrapper {
    @SuppressWarnings("unused")
    java.util.Map<String, OneLevelRoot> actions;
  }

  static class ArrayWrapper {
    @SuppressWarnings("unused")
    OneLevelRoot[] actions;
  }

  // ---------- Opt-out: sealed root with no annotated leaves ----------

  @TemplateDiscriminatorProperty(name = "noKwDiscriminator", group = "operation")
  sealed interface OptOutRoot permits OptOutLeaf {}

  @TemplateSubType(id = "leafWithoutKeywords")
  record OptOutLeaf() implements OptOutRoot {}

  // ---------- Partial-annotation: one leaf has keywords, sibling does not ----------

  @TemplateDiscriminatorProperty(name = "partialDiscriminator", group = "operation")
  sealed interface PartialRoot permits PartialAnnotated, PartialMissing {}

  @TemplateSubType(
      id = "annotated",
      keywords = {"keyword"})
  record PartialAnnotated() implements PartialRoot {}

  @TemplateSubType(id = "missing")
  record PartialMissing() implements PartialRoot {}

  // ---------- Subtype with no @TemplateSubType annotation at all ----------

  @TemplateDiscriminatorProperty(name = "noAnnDiscriminator", group = "operation")
  sealed interface NoAnnRoot permits NoAnnAnnotated, NoAnnPlain {}

  @TemplateSubType(
      id = "annotated",
      keywords = {"keyword"})
  record NoAnnAnnotated() implements NoAnnRoot {}

  record NoAnnPlain() implements NoAnnRoot {}

  // ---------- All inner subtypes ignored ----------

  @TemplateDiscriminatorProperty(name = "outerDiscriminator", group = "operation")
  sealed interface AllIgnoredOuter permits AllIgnoredInner, AllIgnoredLeaf {}

  @TemplateSubType(id = "innerGroup", label = "Inner group")
  @TemplateDiscriminatorProperty(name = "innerDiscriminator", group = "operation")
  sealed interface AllIgnoredInner extends AllIgnoredOuter
      permits AllIgnoredHidden1, AllIgnoredHidden2 {}

  @TemplateSubType(id = "hidden1", ignore = true)
  record AllIgnoredHidden1() implements AllIgnoredInner {}

  @TemplateSubType(id = "hidden2", ignore = true)
  record AllIgnoredHidden2() implements AllIgnoredInner {}

  @TemplateSubType(
      id = "visibleLeaf",
      keywords = {"visible"})
  record AllIgnoredLeaf() implements AllIgnoredOuter {}

  // ---------- Nested discriminator reached via a record-component field ----------
  // The outer hierarchy (NestedFieldOuter) is permits-based, but each outer leaf carries a
  // record component whose type is itself a sealed @TemplateDiscriminatorProperty hierarchy.
  // This mirrors Email's `Smtp.smtpAction → SmtpAction` shape — an intermediate record that
  // is not a leaf but groups further operations through a field hop.

  @TemplateDiscriminatorProperty(name = "outer", group = "operation")
  sealed interface NestedFieldOuter permits NestedFieldA, NestedFieldB {}

  @TemplateSubType(id = "branchA", label = "Branch A")
  record NestedFieldA(NestedFieldAOps ops) implements NestedFieldOuter {}

  @TemplateDiscriminatorProperty(name = "opType", group = "operation")
  sealed interface NestedFieldAOps permits NestedFieldACreate, NestedFieldARead {}

  @TemplateSubType(
      id = "create",
      keywords = {"create A"})
  record NestedFieldACreate() implements NestedFieldAOps {}

  @TemplateSubType(
      id = "read",
      keywords = {"read A"})
  record NestedFieldARead() implements NestedFieldAOps {}

  @TemplateSubType(
      id = "branchB",
      keywords = {"branch B"})
  record NestedFieldB() implements NestedFieldOuter {}

  // ---------- Same shape, but the field-hop is suppressed via @NestedProperties(addNestedPath =
  // false) ----------

  // NestedFieldFlatSibling carries direct keywords so the outer is picked as the operation root
  // by findOperationRoot (which only follows permits when detecting leaf keywords).

  @TemplateDiscriminatorProperty(name = "outerFlat", group = "operation")
  sealed interface NestedFieldFlatOuter permits NestedFieldFlatA, NestedFieldFlatSibling {}

  @TemplateSubType(id = "branchA", label = "Branch A")
  record NestedFieldFlatA(@NestedProperties(addNestedPath = false) NestedFieldFlatAOps ops)
      implements NestedFieldFlatOuter {}

  @TemplateSubType(
      id = "sibling",
      keywords = {"sibling"})
  record NestedFieldFlatSibling() implements NestedFieldFlatOuter {}

  @TemplateDiscriminatorProperty(name = "opTypeFlat", group = "operation")
  sealed interface NestedFieldFlatAOps permits NestedFieldFlatACreate {}

  @TemplateSubType(
      id = "create",
      keywords = {"create A"})
  record NestedFieldFlatACreate() implements NestedFieldFlatAOps {}

  // ---------- Ambiguity: a leaf record has multiple discriminator-bearing fields ----------
  // AmbiSibling has direct keywords so AmbiOuter is picked as the operation root.

  @TemplateDiscriminatorProperty(name = "ambiOuter", group = "operation")
  sealed interface AmbiOuter permits AmbiNode, AmbiSibling {}

  @TemplateSubType(id = "node", label = "Node")
  record AmbiNode(AmbiOpsX opsX, AmbiOpsY opsY) implements AmbiOuter {}

  @TemplateSubType(
      id = "sibling",
      keywords = {"sibling"})
  record AmbiSibling() implements AmbiOuter {}

  @TemplateDiscriminatorProperty(name = "x", group = "operation")
  sealed interface AmbiOpsX permits AmbiX {}

  @TemplateSubType(
      id = "x",
      keywords = {"x"})
  record AmbiX() implements AmbiOpsX {}

  @TemplateDiscriminatorProperty(name = "y", group = "operation")
  sealed interface AmbiOpsY permits AmbiY {}

  @TemplateSubType(
      id = "y",
      keywords = {"y"})
  record AmbiY() implements AmbiOpsY {}

  // ---------- Conflict: a node declares both keywords and a nested discriminator field ----------

  @TemplateDiscriminatorProperty(name = "conflictOuter", group = "operation")
  sealed interface ConflictOuter permits ConflictNode {}

  @TemplateSubType(
      id = "node",
      label = "Node",
      keywords = {"node"})
  record ConflictNode(ConflictOps ops) implements ConflictOuter {}

  @TemplateDiscriminatorProperty(name = "conflictInner", group = "operation")
  sealed interface ConflictOps permits ConflictLeaf {}

  @TemplateSubType(
      id = "leaf",
      keywords = {"leaf"})
  record ConflictLeaf() implements ConflictOps {}

  // ---------- Discriminator name collision: nested-via-field reuses outer's discriminator name
  // ----------

  // FieldCollisionSibling has direct keywords so FieldCollisionOuter is picked as the operation
  // root.

  @TemplateDiscriminatorProperty(name = "sameName", group = "operation")
  sealed interface FieldCollisionOuter permits FieldCollisionNode, FieldCollisionSibling {}

  @TemplateSubType(id = "node", label = "Node")
  record FieldCollisionNode(@NestedProperties(addNestedPath = false) FieldCollisionOps ops)
      implements FieldCollisionOuter {}

  @TemplateSubType(
      id = "sibling",
      keywords = {"sibling"})
  record FieldCollisionSibling() implements FieldCollisionOuter {}

  @TemplateDiscriminatorProperty(name = "sameName", group = "operation")
  sealed interface FieldCollisionOps permits FieldCollisionLeaf {}

  @TemplateSubType(
      id = "leaf",
      keywords = {"leaf"})
  record FieldCollisionLeaf() implements FieldCollisionOps {}

  // ---------- Discriminator name collision: inner reuses outer's discriminator name ----------

  @TemplateDiscriminatorProperty(name = "duplicateName", group = "operation")
  sealed interface CollisionOuter permits CollisionInnerSealed {}

  @TemplateSubType(id = "collidingGroup", label = "Colliding")
  @TemplateDiscriminatorProperty(name = "duplicateName", group = "operation")
  sealed interface CollisionInnerSealed extends CollisionOuter permits CollisionLeaf {}

  @TemplateSubType(
      id = "leaf",
      keywords = {"x"})
  record CollisionLeaf() implements CollisionInnerSealed {}

  @Nested
  class OneLevel {

    @Test
    void emitsFlatLeafStepsAndPresets() {
      StepTreeResult result = StepTreeWalker.walk(OneLevelRoot.class);

      assertThat(result.steps()).hasSize(2);
      assertThat(result.steps()).allMatch(LeafStep.class::isInstance);

      LeafStep upload = (LeafStep) result.steps().get(0);
      assertThat(upload.name()).isEqualTo("Upload object");
      assertThat(upload.description()).isEqualTo("Upload an object");
      assertThat(upload.keywords()).containsExactly("upload", "put");
      assertThat(upload.presetId()).isEqualTo("actionDiscriminator_uploadObject");

      LeafStep download = (LeafStep) result.steps().get(1);
      // No `label` set on DownloadObject → fallback to transformIdIntoLabel(simpleName)
      assertThat(download.name()).isEqualTo("One level download");
      assertThat(download.description()).isNull();
      assertThat(download.keywords()).containsExactly("download", "get");
      assertThat(download.presetId()).isEqualTo("actionDiscriminator_downloadObject");

      assertThat(result.presets()).hasSize(2);
      assertThat(result.presets())
          .extracting(Preset::id)
          .containsExactly(
              "actionDiscriminator_uploadObject", "actionDiscriminator_downloadObject");
      assertThat(result.presets().get(0).properties())
          .containsExactly(Map.entry("actionDiscriminator", "uploadObject"));
    }
  }

  @Nested
  class TwoLevel {

    @Test
    void emitsNestedGroupStepsAndPathPrefixedPresetProperties() {
      StepTreeResult result = StepTreeWalker.walk(TwoLevelInputWrapper.class);

      // Top-level: two group steps
      assertThat(result.steps()).hasSize(2);
      assertThat(result.steps()).allMatch(GroupStep.class::isInstance);

      GroupStep tableGroup = (GroupStep) result.steps().get(0);
      assertThat(tableGroup.name()).isEqualTo("Table");
      assertThat(tableGroup.description()).isEqualTo("Manage tables");
      assertThat(tableGroup.steps()).hasSize(2);
      assertThat(tableGroup.steps()).allMatch(LeafStep.class::isInstance);

      LeafStep createTable = (LeafStep) tableGroup.steps().get(0);
      assertThat(createTable.presetId())
          .isEqualTo("operationGroup_tableOperation_tableOperation_createTable");

      GroupStep itemGroup = (GroupStep) result.steps().get(1);
      assertThat(itemGroup.name()).isEqualTo("Item");
      assertThat(itemGroup.steps()).hasSize(1);

      // Preset properties carry the `input.` path prefix because TwoLevelInputWrapper.input has
      // no @NestedProperties annotation (default addNestedPath = true).
      assertThat(result.presets())
          .extracting(Preset::properties)
          .anySatisfy(
              p -> {
                assertThat(p).containsEntry("input.operationGroup", "tableOperation");
                assertThat(p).containsEntry("input.tableOperation", "createTable");
              });
    }
  }

  @Nested
  class PathPrefixHandling {

    @Test
    void atNestedPathFalse_dropsPrefixFromPresetProperties() {
      StepTreeResult result = StepTreeWalker.walk(NoPathWrapper.class);

      assertThat(result.presets()).hasSize(2);
      assertThat(result.presets().get(0).properties())
          .containsExactly(Map.entry("actionDiscriminator", "uploadObject"));
    }
  }

  @Nested
  class PassThroughUnmatchedSealedTypes {

    @Test
    void findsOperationRootReachableOnlyViaAnotherSealedTypesPermits() {
      // BFS visits NestedAuth (sealed, no operation keywords), passes through its permits to
      // NestedAuthCarryingOps (record), then through its `action` field to OneLevelRoot
      // (sealed with keywords). Without the pass-through, BFS would dead-end at NestedAuth.
      StepTreeResult result = StepTreeWalker.walk(NestedAuthWrapper.class);
      assertThat(result.steps()).hasSize(2);
      assertThat(result.presets().get(0).properties())
          .containsEntry("auth.action.actionDiscriminator", "uploadObject");
    }
  }

  @Nested
  class GenericContainerFields {

    @Test
    void findsSealedRootBehindListField() {
      StepTreeResult result = StepTreeWalker.walk(ListWrapper.class);
      assertThat(result.steps()).hasSize(2);
      assertThat(result.presets().get(0).properties())
          .containsEntry("actions.actionDiscriminator", "uploadObject");
    }

    @Test
    void findsSealedRootBehindOptionalField() {
      StepTreeResult result = StepTreeWalker.walk(OptionalWrapper.class);
      assertThat(result.steps()).hasSize(2);
      assertThat(result.presets().get(0).properties())
          .containsEntry("action.actionDiscriminator", "uploadObject");
    }

    @Test
    void findsSealedRootBehindMapValue() {
      StepTreeResult result = StepTreeWalker.walk(MapWrapper.class);
      assertThat(result.steps()).hasSize(2);
      assertThat(result.presets().get(0).properties())
          .containsEntry("actions.actionDiscriminator", "uploadObject");
    }

    @Test
    void findsSealedRootBehindArrayField() {
      StepTreeResult result = StepTreeWalker.walk(ArrayWrapper.class);
      assertThat(result.steps()).hasSize(2);
      assertThat(result.presets().get(0).properties())
          .containsEntry("actions.actionDiscriminator", "uploadObject");
    }
  }

  @Nested
  class OptIn {

    @Test
    void noLeafHasKeywords_returnsEmpty() {
      StepTreeResult result = StepTreeWalker.walk(OptOutRoot.class);
      assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void nullInputType_returnsEmpty() {
      StepTreeResult result = StepTreeWalker.walk(null);
      assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void partialAnnotation_hardFailsOnLeafMissingKeywords() {
      assertThatThrownBy(() -> StepTreeWalker.walk(PartialRoot.class))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("PartialMissing")
          .hasMessageContaining("keywords");
    }

    @Test
    void missingAnnotation_hardFailsWithMessageMentioningIdAndKeywords() {
      assertThatThrownBy(() -> StepTreeWalker.walk(NoAnnRoot.class))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("NoAnnPlain")
          .hasMessageContaining("@TemplateSubType")
          .hasMessageContaining("id")
          .hasMessageContaining("keywords");
    }
  }

  @Nested
  class InnerGroupAllSubtypesIgnored {

    @Test
    void skipsInnerGroupWhenEverySubtypeIsIgnored() {
      // AllIgnoredInner's permitted subtypes are all @TemplateSubType(ignore = true).
      // The walker should skip the inner group entirely and only emit the sibling leaf.
      StepTreeResult result = StepTreeWalker.walk(AllIgnoredOuter.class);
      assertThat(result.steps()).hasSize(1);
      assertThat(result.steps()).allMatch(LeafStep.class::isInstance);
      LeafStep leaf = (LeafStep) result.steps().get(0);
      assertThat(leaf.presetId()).isEqualTo("outerDiscriminator_visibleLeaf");
      assertThat(result.presets()).hasSize(1);
    }
  }

  @Nested
  class DiscriminatorNameCollision {

    @Test
    void throwsWhenInnerSealedReusesOuterDiscriminatorName() {
      // CollisionInnerSealed uses the same @TemplateDiscriminatorProperty(name = "duplicateName")
      // as its outer parent. Without a clear error, the inner assignment would silently overwrite
      // the outer's preset value, corrupting the resulting preset.
      assertThatThrownBy(() -> StepTreeWalker.walk(CollisionOuter.class))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("CollisionInnerSealed")
          .hasMessageContaining("duplicateName")
          .hasMessageContaining("collides");
    }
  }

  @Nested
  class NestedDiscriminatorViaField {

    @Test
    void treatsIntermediateRecordAsGroupAndPrefixesPresetKeyWithFieldName() {
      // NestedFieldA is a record (not sealed). It holds an `ops` field whose type is
      // NestedFieldAOps (sealed @TemplateDiscriminatorProperty). The walker must treat
      // NestedFieldA as a group and update the preset key path with the field name.
      StepTreeResult result = StepTreeWalker.walk(NestedFieldOuter.class);

      assertThat(result.steps()).hasSize(2);
      assertThat(result.steps().get(0)).isInstanceOf(GroupStep.class);
      GroupStep branchA = (GroupStep) result.steps().get(0);
      assertThat(branchA.name()).isEqualTo("Branch A");
      assertThat(branchA.steps()).hasSize(2);
      assertThat(branchA.steps()).allMatch(LeafStep.class::isInstance);

      LeafStep createStep = (LeafStep) branchA.steps().get(0);
      assertThat(createStep.presetId()).isEqualTo("outer_branchA_opType_create");

      // Sibling branchB has direct keywords — it's a true leaf even though its sibling is a group.
      assertThat(result.steps().get(1)).isInstanceOf(LeafStep.class);

      // The preset for the nested-via-field leaf carries the `ops.` path prefix because the
      // field has no @NestedProperties annotation (default addNestedPath = true).
      assertThat(result.presets())
          .extracting(Preset::properties)
          .anySatisfy(
              p -> {
                assertThat(p).containsEntry("outer", "branchA");
                assertThat(p).containsEntry("ops.opType", "create");
              });
    }

    @Test
    void honoursAddNestedPathFalseWhenRecursingThroughField() {
      // The field carries @NestedProperties(addNestedPath = false) — the discriminator key for
      // the nested hierarchy must NOT be prefixed with the field name.
      StepTreeResult result = StepTreeWalker.walk(NestedFieldFlatOuter.class);

      Preset flatPreset =
          result.presets().stream()
              .filter(p -> p.id().equals("outerFlat_branchA_opTypeFlat_create"))
              .findFirst()
              .orElseThrow();
      assertThat(flatPreset.properties())
          .containsEntry("opTypeFlat", "create")
          .doesNotContainKey("ops.opTypeFlat");
    }

    @Test
    void throwsOnAmbiguousMultipleNestedDiscriminatorFields() {
      assertThatThrownBy(() -> StepTreeWalker.walk(AmbiOuter.class))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("AmbiNode")
          .hasMessageContaining("more than one")
          .hasMessageContaining("opsX")
          .hasMessageContaining("opsY");
    }

    @Test
    void throwsWhenNodeHasBothKeywordsAndNestedDiscriminatorField() {
      assertThatThrownBy(() -> StepTreeWalker.walk(ConflictOuter.class))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("ConflictNode")
          .hasMessageContaining("keywords")
          .hasMessageContaining("ops");
    }

    @Test
    void throwsWhenNestedFieldDiscriminatorNameCollidesWithOuter() {
      // The nested discriminator (sameName) collides with the outer's name at the same path.
      // The collision check in buildSealedGroup must fire for field-reached groups too.
      assertThatThrownBy(() -> StepTreeWalker.walk(FieldCollisionOuter.class))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("sameName")
          .hasMessageContaining("collides");
    }
  }

  @Nested
  class WalkerDoesNotRequireGroupOperation {

    @TemplateDiscriminatorProperty(name = "actionDiscriminator", group = "action")
    sealed interface NonOpGroupRoot permits NonOpGroupLeaf {}

    @TemplateSubType(
        id = "leaf",
        keywords = {"x"})
    record NonOpGroupLeaf() implements NonOpGroupRoot {}

    @Test
    void walkerEmitsTreeEvenWhenDiscriminatorGroupIsNotOperation() {
      // The validator enforces group = "operation" on the JSON side; the walker no longer requires
      // it. Opt-in is the keyword annotation on the leaf.
      StepTreeResult result = StepTreeWalker.walk(NonOpGroupRoot.class);
      assertThat(result.steps()).hasSize(1);
      assertThat(result.presets()).hasSize(1);
    }
  }
}
