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
package io.camunda.connector.http.client.authentication;

public class OAuthConstants {
  public static final String GRANT_TYPE = "grant_type";
  public static final String CLIENT_ID = "client_id";
  public static final String CLIENT_SECRET = "client_secret";
  public static final String AUDIENCE = "audience";
  public static final String SCOPE = "scope";
  public static final String REFRESH_TOKEN = "refresh_token";
  public static final String ACCESS_TOKEN = "access_token";
  public static final String EXPIRES_IN = "expires_in";
  public static final String BASIC_AUTH_HEADER = "basicAuthHeader";
  public static final String CREDENTIALS_BODY = "credentialsBody";
  public static final String ERROR = "error";
  public static final String ERROR_DESCRIPTION = "error_description";
  public static final String INVALID_GRANT = "invalid_grant";
  public static final String INTERACTION_REQUIRED = "interaction_required";
}
