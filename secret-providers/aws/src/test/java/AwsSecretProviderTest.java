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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.camunda.connector.api.secret.SecretProvider;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

public class AwsSecretProviderTest {
  @Test
  public void shouldFindSecretProviderImpl() {
    var a = ServiceLoader.load(SecretProvider.class);
    assertEquals(1, a.stream().count());
  }

  @Test
  public void stsShouldBeOnClasspathForIrsaSupport() {
    // AwsSecretProvider uses DefaultCredentialsProvider which includes
    // WebIdentityTokenFileCredentialsProvider (IRSA) only when software.amazon.awssdk:sts
    // is on the classpath. Without it IRSA is silently skipped.
    assertThatCode(() -> Class.forName("software.amazon.awssdk.services.sts.StsClient"))
        .as("software.amazon.awssdk:sts must be on the classpath for IRSA support")
        .doesNotThrowAnyException();
  }
}
