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
package io.camunda.connector.impl.secret;

import static org.mockito.Mockito.*;

import java.util.function.Function;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class SecretUtilTests {

  @ParameterizedTest
  @CsvSource({
    "secrets.test,test, true",
    "secrets.TEST,TEST, true",
    "secrets.A/B,A/B, true",
    "secrets.A.B,A.B, true",
    "{secrets.TEST},TEST, true",
    "secrets.TEST0,TEST0, true",
    "secrets.TEST-0,TEST-0, true",
    "secrets.TEST_0,TEST_0, true",
    "secrets.TEST_TEST,TEST_TEST, true",
    "secrets.a_b_c_d_e_f,a_b_c_d_e_f, true",
    "secrets.a.b.c.d.e.f,a.b.c.d.e.f, true",
    "secrets.TEST TEST,TEST,true",
    "secrets._TEST,,false",
    "secrets./TEST,,false",
    "secrets.-TEST,,false",
    "secrets..TEST,,false",
    "secrets.,,false",
    "secrets..,,false",
    "secrets.?,,false"
  })
  void testSecretPattern(String input, String secret, Boolean shouldDetect) {
    var secretReplacer = mock(Function.class);
    SecretUtil.replaceSecrets(input, secretReplacer);
    if (shouldDetect) {
      verify(secretReplacer).apply(secret);
    } else {
      verifyNoInteractions(secretReplacer);
    }
  }
}
