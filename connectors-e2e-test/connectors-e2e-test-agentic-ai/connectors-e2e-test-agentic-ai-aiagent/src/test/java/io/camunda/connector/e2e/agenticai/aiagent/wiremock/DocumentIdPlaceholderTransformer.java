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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces document id placeholders in a stubbed model response with the document ids found in the
 * request the model is answering.
 *
 * <p>Document ids are derived from the identity of the document, which a scripted model cannot know
 * up front. A placeholder {@code @@DOC_ID_n@@} resolves to the n-th distinct document id (order of
 * first appearance, zero based) in the request body, wherever it appears in it, so the escaping of
 * the surrounding text does not matter. A placeholder without a matching id is left untouched,
 * which surfaces as an unknown id in the agent.
 *
 * <p>The transformer applies to every stub, so the scripted model of any provider can use the
 * placeholders; a response without a placeholder is left untouched.
 */
public final class DocumentIdPlaceholderTransformer implements ResponseDefinitionTransformerV2 {

  public static final String NAME = "document-id-placeholders";

  private static final Pattern DOCUMENT_ID = Pattern.compile("doc_[0-9a-f]{12}");
  private static final Pattern PLACEHOLDER = Pattern.compile("@@DOC_ID_(\\d+)@@");

  /** The placeholder for the n-th document id (zero based) of the request. */
  public static String documentId(int index) {
    return "@@DOC_ID_" + index + "@@";
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean applyGlobally() {
    return true;
  }

  @Override
  public ResponseDefinition transform(ServeEvent serveEvent) {
    final var definition = serveEvent.getResponseDefinition();
    final var body = definition.getBody();
    if (body == null || !body.contains("@@DOC_ID_")) {
      return definition;
    }

    final var ids = new LinkedHashSet<String>();
    final var idMatcher = DOCUMENT_ID.matcher(serveEvent.getRequest().getBodyAsString());
    while (idMatcher.find()) {
      ids.add(idMatcher.group());
    }
    final var idList = ids.stream().toList();

    final var resolved =
        PLACEHOLDER
            .matcher(body)
            .replaceAll(match -> {
              final int index = Integer.parseInt(match.group(1));
              return index < idList.size()
                  ? Matcher.quoteReplacement(idList.get(index))
                  : Matcher.quoteReplacement(match.group());
            });
    return ResponseDefinitionBuilder.like(definition).but().withBody(resolved).build();
  }
}
