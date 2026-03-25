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
package io.camunda.connector.generator.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class OpenApiGenerationSourceTest {

  // language=yaml
  private static final String SPEC_WITH_INTERNAL_REF =
      """
      openapi: 3.0.3
      info:
        title: Test API
        version: 1.0.0
      servers:
        - url: https://api.example.com/v1
      paths:
        /users:
          get:
            operationId: listUsers
            responses:
              '200':
                description: A list of users.
                content:
                  application/json:
                    schema:
                      $ref: '#/components/schemas/User'
      components:
        schemas:
          User:
            type: object
            properties:
              id:
                type: string
                format: uuid
              name:
                type: string
                example: Jane Doe
      """;

  /**
   * KEY security test. Verifies that external file references are NOT followed when {@code
   * --no-resolve-refs} is passed.
   *
   * <p>The test writes two real files into a temp directory: an OpenAPI spec referencing an
   * adjacent schema file via a relative {@code $ref: './user-schema.json'}, and the schema file
   * itself containing a sentinel property.
   *
   * <p>The parser behaviour depends on the {@code resolveExternalRefs} option:
   *
   * <ul>
   *   <li>{@code resolveExternalRefs=true} (default): the parser reads {@code user-schema.json},
   *       pulls it into {@code components}, and rewrites the {@code $ref} to {@code
   *       #/components/schemas/user-schema}. The assertion {@code isEqualTo("./user-schema.json")}
   *       then <strong>fails</strong>, exposing the bug.
   *   <li>{@code resolveExternalRefs=false} (opt-in via {@code --no-resolve-refs}): the {@code
   *       $ref} is left untouched as the original relative path. Both assertions pass.
   * </ul>
   */
  @Test
  void shouldNotFollowExternalFileRef_whenNoResolveRefsIsSet(@TempDir Path tempDir)
      throws IOException {
    // language=json
    Files.writeString(
        tempDir.resolve("user-schema.json"),
        """
        {
          "type": "object",
          "properties": {
            "sentinelProperty": { "type": "string" }
          }
        }
        """);

    // language=yaml
    Files.writeString(
        tempDir.resolve("openapi.yaml"),
        """
        openapi: 3.0.3
        info:
          title: Test
          version: 1.0.0
        servers:
          - url: https://api.example.com/v1
        paths:
          /users:
            post:
              operationId: createUser
              requestBody:
                required: true
                content:
                  application/json:
                    schema:
                      $ref: './user-schema.json'
              responses:
                '201':
                  description: Created
        """);

    // --no-resolve-refs sets Options.resolveExternalRefs=false, preventing the parser
    // from following relative $refs to files on the local filesystem.
    var source =
        new OpenApiGenerationSource(
            List.of(tempDir.resolve("openapi.yaml").toString(), "--no-resolve-refs"));
    var schema =
        source
            .openAPI()
            .getPaths()
            .get("/users")
            .getPost()
            .getRequestBody()
            .getContent()
            .get("application/json")
            .getSchema();

    // With resolveExternalRefs=false: $ref is unchanged — the external file was never read.
    // With resolveExternalRefs=true: $ref is rewritten to '#/components/schemas/user-schema'.
    assertThat(schema.get$ref())
        .as("External $ref must not be rewritten (local file must not be read)")
        .isEqualTo("./user-schema.json");

    // Complementary: the external schema must not have been pulled into components.
    var schemas =
        source.openAPI().getComponents() != null
            ? source.openAPI().getComponents().getSchemas()
            : null;
    assertThat(schemas)
        .as("External schema must not be imported into components")
        .satisfiesAnyOf(
            s -> assertThat(s).isNull(), s -> assertThat(s).doesNotContainKey("user-schema"));
  }

  /**
   * Verifies that the default behaviour (backward-compatible) still resolves external file
   * references when {@code --no-resolve-refs} is NOT passed.
   */
  @Test
  void shouldFollowExternalFileRef_byDefault(@TempDir Path tempDir) throws IOException {
    // language=json
    Files.writeString(
        tempDir.resolve("user-schema.json"),
        """
        {
          "type": "object",
          "properties": {
            "sentinelProperty": { "type": "string" }
          }
        }
        """);

    // language=yaml
    Files.writeString(
        tempDir.resolve("openapi.yaml"),
        """
        openapi: 3.0.3
        info:
          title: Test
          version: 1.0.0
        servers:
          - url: https://api.example.com/v1
        paths:
          /users:
            post:
              operationId: createUser
              requestBody:
                required: true
                content:
                  application/json:
                    schema:
                      $ref: './user-schema.json'
              responses:
                '201':
                  description: Created
        """);

    // No --no-resolve-refs flag: resolveExternalRefs defaults to true (backward-compatible).
    var source = new OpenApiGenerationSource(List.of(tempDir.resolve("openapi.yaml").toString()));
    var schema =
        source
            .openAPI()
            .getPaths()
            .get("/users")
            .getPost()
            .getRequestBody()
            .getContent()
            .get("application/json")
            .getSchema();

    // $ref is rewritten to #/components/schemas/... because the file was read and inlined.
    assertThat(schema.get$ref())
        .as("External $ref must be rewritten when resolveExternalRefs=true (default)")
        .startsWith("#/components/schemas/");
  }

  /**
   * Verifies that {@link OpenApiOutboundTemplateGenerator#prepareInput} injects {@code
   * --no-resolve-refs} when the generator is constructed with {@code resolveExternalRefs=false}, so
   * callers that use the generator (e.g. Web Modeler) only need to configure the generator — they
   * do not have to know about the CLI flag themselves.
   */
  @Test
  void generatorWithResolveDisabled_shouldNotFollowExternalFileRef(@TempDir Path tempDir)
      throws IOException {
    Files.writeString(
        tempDir.resolve("user-schema.json"),
        """
        { "type": "object", "properties": { "sentinelProperty": { "type": "string" } } }
        """);

    // language=yaml
    Files.writeString(
        tempDir.resolve("openapi.yaml"),
        """
        openapi: 3.0.3
        info:
          title: Test
          version: 1.0.0
        servers:
          - url: https://api.example.com/v1
        paths:
          /users:
            post:
              operationId: createUser
              requestBody:
                required: true
                content:
                  application/json:
                    schema:
                      $ref: './user-schema.json'
              responses:
                '201':
                  description: Created
        """);

    var generator = new OpenApiOutboundTemplateGenerator(/* resolveExternalRefs= */ false);
    var source = generator.prepareInput(List.of(tempDir.resolve("openapi.yaml").toString()));

    var schema =
        source
            .openAPI()
            .getPaths()
            .get("/users")
            .getPost()
            .getRequestBody()
            .getContent()
            .get("application/json")
            .getSchema();

    assertThat(schema.get$ref())
        .as("External $ref must not be rewritten when generator is configured with resolve=false")
        .isEqualTo("./user-schema.json");
  }

  /**
   * Verifies that the default {@link OpenApiOutboundTemplateGenerator} (no-arg constructor) still
   * resolves external refs, preserving backward compatibility for existing callers.
   */
  @Test
  void generatorDefault_shouldFollowExternalFileRef(@TempDir Path tempDir) throws IOException {
    Files.writeString(
        tempDir.resolve("user-schema.json"),
        """
        { "type": "object", "properties": { "sentinelProperty": { "type": "string" } } }
        """);

    // language=yaml
    Files.writeString(
        tempDir.resolve("openapi.yaml"),
        """
        openapi: 3.0.3
        info:
          title: Test
          version: 1.0.0
        servers:
          - url: https://api.example.com/v1
        paths:
          /users:
            post:
              operationId: createUser
              requestBody:
                required: true
                content:
                  application/json:
                    schema:
                      $ref: './user-schema.json'
              responses:
                '201':
                  description: Created
        """);

    var generator = new OpenApiOutboundTemplateGenerator(); // default: resolve=true
    var source = generator.prepareInput(List.of(tempDir.resolve("openapi.yaml").toString()));

    var schema =
        source
            .openAPI()
            .getPaths()
            .get("/users")
            .getPost()
            .getRequestBody()
            .getContent()
            .get("application/json")
            .getSchema();

    assertThat(schema.get$ref())
        .as("External $ref must be rewritten by default (backward-compatible behaviour)")
        .startsWith("#/components/schemas/");
  }

  /**
   * Regression guard. Ensures that using {@code resolve=false} does not break access to schemas
   * declared inline within the {@code components} section of the same document.
   */
  @Test
  void shouldStillParseInlineSchemasFromComponents() {
    var source = new OpenApiGenerationSource(List.of(SPEC_WITH_INTERNAL_REF));

    assertThat(source.openAPI()).isNotNull();
    assertThat(source.openAPI().getComponents().getSchemas()).containsKey("User");

    var userSchema = source.openAPI().getComponents().getSchemas().get("User");
    assertThat(userSchema.getProperties())
        .as("Inline component schema properties must be parsed correctly")
        .containsKeys("id", "name");
  }
}
