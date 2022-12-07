package io.camunda.connector.slack.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class DataLookupServiceTest {
    @Test
    public void shouldConvertStringToList() {
        String stringToConvertToList = "test@test.com,@test1, test2 ,  @test3 ";
        List<String> result = DataLookupService.convertStringToList(stringToConvertToList);
        assertEquals(4, result.size());
        assertEquals("test3", result.get(3));
    }

    @ParameterizedTest
    @CsvSource({
            "test@test.com,true",
            "t.e.s.t@test.com,true",
            "test@,false",
            "@test,false",
            "test.com,false",
            "test,false"})
    public void shouldReturnTrueForEmailCheck(String email, boolean result) {
        assertEquals(DataLookupService.isEmail(email), result);
    }
}
