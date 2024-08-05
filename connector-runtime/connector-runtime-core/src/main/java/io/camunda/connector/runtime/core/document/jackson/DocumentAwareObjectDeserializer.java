package io.camunda.connector.runtime.core.document.jackson;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.camunda.connector.api.document.DocumentReference;
import java.io.IOException;
import java.util.Set;

public class DocumentAwareObjectDeserializer extends StdDeserializer<Object> implements
    ContextualDeserializer {

  protected DocumentAwareObjectDeserializer(JavaType valueType) {
    super(valueType);
  }

  private final SimpleDocumentDeserializer documentDeserializer = new SimpleDocumentDeserializer();

  @Override
  public Object deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException, JacksonException {

    if (_valueType.isTypeOrSubTypeOf(Document.class)) {
      return documentDeserializer.deserialize(jsonParser, deserializationContext);
    }
    var reference = deserializationContext.readTreeAsValue(jsonParser.readValueAsTree(), DocumentReference.class);
    if (reference.operation().isPresent()) {
      var operation = reference.operation().get();
    }

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

  private

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
    return new DocumentAwareObjectDeserializer(ctxt.getContextualType());
  }
}
