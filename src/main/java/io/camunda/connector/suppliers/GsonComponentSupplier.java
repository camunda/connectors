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
package io.camunda.connector.suppliers;

import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.Optional;

public final class GsonComponentSupplier {

  private static final Gson GSON =
      new GsonBuilder()
          .registerTypeAdapter(MessageAttributeValue.class, new MessageAttributeValueDeserializer())
          .create();

  private GsonComponentSupplier() {}

  public static Gson gsonInstance() {
    return GSON;
  }

  protected static class MessageAttributeValueDeserializer
      implements JsonDeserializer<MessageAttributeValue> {

    @Override
    public MessageAttributeValue deserialize(
        final JsonElement jsonElement,
        final Type type,
        final JsonDeserializationContext jsonDeserializationContext)
        throws JsonParseException {
      MessageAttributeValue mav = new MessageAttributeValue();
      JsonObject jObject = jsonElement.getAsJsonObject();
      // While there are currently multiple field types in the MessageAttributeValue, the actual
      // support is very limited to DataType, StringValue and BinaryValue.
      // At the same time, we do not plan to support custom binary types.
      // See more at:
      // https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_MessageAttributeValue.html
      Optional.ofNullable(jObject.get("DataType").getAsString()).ifPresent(mav::setDataType);
      Optional.ofNullable(jObject.get("StringValue").getAsString()).ifPresent(mav::setStringValue);
      return mav;
    }
  }
}
