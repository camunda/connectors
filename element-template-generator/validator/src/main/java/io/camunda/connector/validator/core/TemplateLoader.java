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
package io.camunda.connector.validator.core;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Parses element-template JSON files. Strict mode rejects duplicate object keys — which Jackson
 * would otherwise silently coalesce (last-wins) — and surfaces them as a {@code duplicate-keys}
 * finding instead of {@code json-parse}. Mirrors the duplicate-key check that the Camunda Web
 * Modeler runs at editor save-time.
 */
public final class TemplateLoader {

  public static final String DUPLICATE_KEYS_RULE = "duplicate-keys";
  public static final String JSON_PARSE_RULE = "json-parse";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    MAPPER.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
  }

  private TemplateLoader() {}

  public static Result load(Path path) {
    try {
      return new Result(MAPPER.readTree(path.toFile()), null);
    } catch (JsonParseException e) {
      String original = e.getOriginalMessage();
      if (original != null && original.startsWith("Duplicate field")) {
        JsonLocation loc = e.getLocation();
        String where =
            loc == null ? "" : " at line " + loc.getLineNr() + ", column " + loc.getColumnNr();
        return new Result(
            null, Finding.error(path, "/", DUPLICATE_KEYS_RULE, original + where + "."));
      }
      return new Result(
          null,
          Finding.error(path, "/", JSON_PARSE_RULE, "Failed to parse JSON: " + e.getMessage()));
    } catch (IOException e) {
      return new Result(
          null,
          Finding.error(path, "/", JSON_PARSE_RULE, "Failed to parse JSON: " + e.getMessage()));
    }
  }

  public record Result(JsonNode node, Finding finding) {
    public boolean ok() {
      return finding == null;
    }
  }
}
