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
package io.camunda.connector.http.base.cloudfunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2Credentials;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class CloudFunctionCredentialsCacheTest {

  @Test
  public void shouldCallCredentialsSupplier_whenEmptyCache() {
    // given
    CloudFunctionCredentialsCache cloudFunctionCredentialsCache =
        new CloudFunctionCredentialsCache();
    AtomicInteger credentialsSupplierCalledCnt = new AtomicInteger(0);
    var fakeCredentials = mock(OAuth2Credentials.class);
    var dateNow = new Date();
    when(fakeCredentials.getAccessToken()).thenReturn(new AccessToken("fakeToken", dateNow));

    // when
    var credentials =
        cloudFunctionCredentialsCache.get(
            getOAuth2CredentialsSupplier(fakeCredentials, credentialsSupplierCalledCnt));

    // then
    assertThat(credentialsSupplierCalledCnt.get()).isEqualTo(1);
    assertThat(credentials).isNotNull();
    assertThat(credentials.getAccessToken()).isEqualTo(new AccessToken("fakeToken", dateNow));
  }

  @Test
  public void shouldNotCallCredentialsSupplier_whenCacheFilled() {
    // given
    CloudFunctionCredentialsCache cloudFunctionCredentialsCache =
        new CloudFunctionCredentialsCache();
    AtomicInteger credentialsSupplierCalledCnt = new AtomicInteger(0);
    var fakeCredentials = mock(OAuth2Credentials.class);
    var dateNow = new Date();
    when(fakeCredentials.getAccessToken()).thenReturn(new AccessToken("fakeToken", dateNow));

    // when
    Supplier<OAuth2Credentials> oAuth2CredentialsSupplier =
        getOAuth2CredentialsSupplier(fakeCredentials, credentialsSupplierCalledCnt);
    cloudFunctionCredentialsCache.get(oAuth2CredentialsSupplier);
    var credentials = cloudFunctionCredentialsCache.get(oAuth2CredentialsSupplier);

    // then
    assertThat(credentialsSupplierCalledCnt.get()).isEqualTo(1);
    assertThat(credentials).isNotNull();
    assertThat(credentials.getAccessToken()).isEqualTo(new AccessToken("fakeToken", dateNow));
  }

  @Test
  public void shouldCallCredentialsSupplier_whenTokenExpired() throws IOException {
    // given
    CloudFunctionCredentialsCache cloudFunctionCredentialsCache =
        new CloudFunctionCredentialsCache();
    AtomicInteger credentialsSupplierCalledCnt = new AtomicInteger(0);
    var fakeCredentials = mock(OAuth2Credentials.class, Mockito.RETURNS_DEEP_STUBS);
    Date expiredDate = new Date(System.currentTimeMillis() - 3600 * 1000); // 1 hour in the past
    Date validDate = new Date(System.currentTimeMillis() + 3600 * 1000); // 1 hour in the future
    when(fakeCredentials.getAccessToken())
        .thenReturn(new AccessToken("fakeToken", validDate))
        .thenReturn(new AccessToken("fakeToken", expiredDate));

    // when
    Supplier<OAuth2Credentials> oAuth2CredentialsSupplier =
        getOAuth2CredentialsSupplier(fakeCredentials, credentialsSupplierCalledCnt);
    // first call to fill the cache
    var credentials = cloudFunctionCredentialsCache.get(oAuth2CredentialsSupplier);
    // second call to get the cached credentials with expired token
    credentials = cloudFunctionCredentialsCache.get(oAuth2CredentialsSupplier);

    // then
    assertThat(credentialsSupplierCalledCnt.get()).isEqualTo(1);
    assertThat(credentials).isNotNull();
    assertThat(credentials.getAccessToken()).isEqualTo(new AccessToken("fakeToken", expiredDate));
    verify(fakeCredentials, times(1)).refreshIfExpired();
  }

  private Supplier<OAuth2Credentials> getOAuth2CredentialsSupplier(
      OAuth2Credentials fakeCredentials, AtomicInteger credentialsSupplierCalled) {
    credentialsSupplierCalled.incrementAndGet();
    return () -> fakeCredentials;
  }
}
