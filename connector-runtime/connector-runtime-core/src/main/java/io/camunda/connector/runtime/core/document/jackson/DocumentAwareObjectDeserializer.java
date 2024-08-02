package io.camunda.connector.runtime.core.document.jackson;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public class DocumentAwareObjectDeserializer extends JsonDeserializer<Object> {

  @Override
  public Object deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException, JacksonException {

    // if it's a string or similar -> bind eagerly
    // if the type is object -> execute the operation lazily during serialization

    // types to support:
    // - String for base64
    // String for create url
    // byte array
    // input stream
    // document reference object
    // array of documents / references

    return null;
  }
}
