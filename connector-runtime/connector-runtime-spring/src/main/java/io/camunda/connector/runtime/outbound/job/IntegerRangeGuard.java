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
package io.camunda.connector.runtime.outbound.job;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import io.camunda.connector.api.error.ConnectorInputException;
import java.io.IOException;

/**
 * Zeebe stores an integer variable as a signed 64-bit long, and refuses a command whose variables
 * hold a larger one: above 2^64 - 1 or below -2^63 the gateway cannot convert it to MessagePack,
 * and from 2^63 to 2^64 - 1 the broker rejects the record. A connector result can hold such a
 * number, for example an ID in a JSON response. A refused completion leaves the job activated until
 * it times out, and Zeebe then hands it out again, so the connector runs again with no incident.
 *
 * <p>This check reads the variables as JSON, so it also finds such a number inside a response
 * object, not only in maps and lists.
 */
final class IntegerRangeGuard {

  private IntegerRangeGuard() {}

  /**
   * @param jsonFactory the factory of the mapper that wrote {@code json}
   * @param json the variables, serialized
   * @throws ConnectorInputException naming the first value Zeebe would refuse: an integer outside
   *     the range of {@code long}, with its JSON pointer, or a name longer than Zeebe reads
   */
  static void check(JsonFactory jsonFactory, byte[] json) throws IOException {
    String refused = firstValueZeebeRefuses(jsonFactory, json);
    if (refused != null) {
      throw new ConnectorInputException(
          "Output variables hold %s. Leave it out of the output, for example with a result expression."
              .formatted(refused));
    }
  }

  /**
   * Like {@link #check(JsonFactory, byte[])}, for the variables of an element that an ad-hoc
   * sub-process activates, such as an AI Agent's tool call. The completion command sends them too.
   *
   * @param elementId the element to activate, named in the error
   */
  static void checkElementActivation(JsonFactory jsonFactory, byte[] json, String elementId)
      throws IOException {
    String refused = firstValueZeebeRefuses(jsonFactory, json);
    if (refused != null) {
      throw new ConnectorInputException(
          "The variables for the activated element '%s' hold %s.".formatted(elementId, refused));
    }
  }

  /**
   * Describes the first value in {@code json} that Zeebe refuses, or returns {@code null}.
   *
   * <p>Zeebe's {@code MsgPackConverter} lifts Jackson's limits on number length, string length and
   * nesting depth, so this read sizes them to the payload, on a copy of the factory. With the
   * default limits, an integer longer than 1000 digits would stop the read with a {@link
   * StreamConstraintsException} before its range is checked, a generic error that costs a retry.
   * Names keep the factory's limit, Jackson's default of 50,000 characters, which Zeebe applies
   * too, so a longer name is reported as a value Zeebe refuses.
   */
  private static String firstValueZeebeRefuses(JsonFactory jsonFactory, byte[] json)
      throws IOException {
    StreamReadConstraints payloadSizedLimits =
        jsonFactory
            .streamReadConstraints()
            .rebuild()
            .maxNumberLength(json.length)
            .maxStringLength(json.length)
            .maxNestingDepth(json.length)
            .build();
    JsonFactory scanFactory =
        jsonFactory.rebuild().streamReadConstraints(payloadSizedLimits).build();
    try (JsonParser parser = scanFactory.createParser(json)) {
      JsonToken token;
      while ((token = parser.nextToken()) != null) {
        if (token == JsonToken.VALUE_NUMBER_INT
            && parser.getNumberType() == JsonParser.NumberType.BIG_INTEGER) {
          return "an integer outside the signed 64-bit range that Zeebe accepts, at '%s'"
              .formatted(parser.getParsingContext().pathAsPointer());
        }
      }
    } catch (StreamConstraintsException e) {
      return "a value that Zeebe refuses: " + e.getOriginalMessage();
    }
    return null;
  }
}
