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
package io.camunda.connector.inbound.security.signature;

public enum HMACAlgoCustomerChoice {

    sha_1("HmacSHA1", "sha1"),
    sha_256("HmacSHA256", "sha256"),
    sha_512("HmacSHA512", "sha512");

    private final String algoReference;
    private final String tag;

    HMACAlgoCustomerChoice(final String algoReference, final String tag) {
        this.algoReference = algoReference;
        this.tag = tag;
    }

    public String getAlgoReference() {
        return algoReference;
    }

    public String getTag() {
        return tag;
    }
}
