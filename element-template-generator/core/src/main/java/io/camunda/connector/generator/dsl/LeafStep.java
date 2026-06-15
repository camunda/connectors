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
package io.camunda.connector.generator.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;

/**
 * Terminal step node referencing exactly one {@link Preset} via {@link #presetId()}. Carries the
 * search aliases ({@code keywords}) for the Modeler search/discovery UI. Keywords are required and
 * non-empty by contract; the walker rejects leaves missing them at generation time.
 */
@JsonInclude(Include.NON_NULL)
public record LeafStep(String name, String description, List<String> keywords, String presetId)
    implements Step {

  public LeafStep {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("LeafStep name must be non-blank");
    }
    if (keywords == null || keywords.isEmpty()) {
      throw new IllegalArgumentException(
          "LeafStep \"" + name + "\" must have a non-empty keywords list");
    }
    if (presetId == null || presetId.isBlank()) {
      throw new IllegalArgumentException(
          "LeafStep \"" + name + "\" must have a non-blank presetId");
    }
  }
}
