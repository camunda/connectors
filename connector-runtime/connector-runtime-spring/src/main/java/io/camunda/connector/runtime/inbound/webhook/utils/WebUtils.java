package io.camunda.connector.runtime.inbound.webhook.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.runtime.core.feel.jackson.JacksonModuleFeelFunction;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class WebUtils {

    private static final ObjectMapper MAPPER =
            new ObjectMapper()
                    //.registerModule(new Jdk8Module())
                    .registerModule(new JavaTimeModule())
                    .registerModule(new JacksonModuleFeelFunction())
                    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);


    WebUtils(){}

    public static String extractContentType(Map<String, String> headers) {
        var caseInsensitiveMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        caseInsensitiveMap.putAll(headers);
        return caseInsensitiveMap.getOrDefault(HttpHeaders.CONTENT_TYPE, "").toString();
    }

    public static Map transformRawBodyToMap(byte[] rawBody, String contentTypeHeader) {
        if (rawBody == null) {
            return Collections.emptyMap();
        }

        if (MediaType.FORM_DATA.toString().equalsIgnoreCase(contentTypeHeader)) {
            String bodyAsString =
                    URLDecoder.decode(new String(rawBody, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            return Arrays.stream(bodyAsString.split("&"))
                    .filter(Objects::nonNull)
                    .map(param -> param.split("="))
                    .collect(Collectors.toMap(param -> param[0], param -> param.length == 1 ? "" : param[1]));
        } else {
            // Do our best to parse to JSON (throws exception otherwise)
            try {
                return getMapperInstance().readValue(rawBody, Map.class);
            } catch (IOException e) {
                throw new ConnectorException("Couldn't parse content");
            }
        }
    }
    
    public static ObjectMapper getMapperInstance() {
        return MAPPER;
    }
}
