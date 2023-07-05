/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

public abstract class BaseTest {

  private static final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);

  protected static final String SECRET_BEARER_TOKEN = "MyToken";
  protected static final String SECRET_REFRESH_TOKEN = "MyOauthRefresh";
  protected static final String ACTUAL_BEARER_TOKEN = "MyRealToken";
  protected static final String ACTUAL_REFRESH_TOKEN = "MyRealRefreshTokenValue";
  protected static final String SECRET_OAUTH_CLIENT_ID = "MyOauthClient";
  protected static final String ACTUAL_OAUTH_CLIENT_ID = "MyRealOauthClientValue";
  protected static final String SECRET_OAUTH_SECRET_ID = "MyOauthSecret";
  protected static final String ACTUAL_OAUTH_SECRET_ID = "MyRealOauthSecretValue";
  protected static final String FOLDER_NAME = "MyNewFolder";
  protected static final String FILE_NAME = "MyNewFile";
  protected static final String PARENT_ID = "optional my idFolderParent";
  protected static final String FILE_ID = "123456789654321564897";
  protected static final String FILE_URL = "https://docs.google.com/document/d/123456644";

  @SuppressWarnings("unchecked")
  protected static Stream<String> loadTestCasesFromResourceFile(final String fileWithTestCasesUri)
      throws IOException {
    return objectMapper
        .readValue(new File(fileWithTestCasesUri), new TypeReference<List<JsonNode>>() {})
        .stream()
        .map(JsonNode::toString);
  }
}
