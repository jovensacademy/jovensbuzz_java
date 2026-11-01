package com.jovensbuzz;

import com.jovensbuzz.common.exception.BadRequestException;
import com.jovensbuzz.common.validation.InternationalPhoneValidator;
import com.jovensbuzz.common.validation.InternationalPhoneValidator.NormalizedPhone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class InternationalPhoneValidatorTest {

    @ParameterizedTest(name = "{1} {0} -> {2}")
    @CsvSource({
            "'(201) 555-0123', US, +12015550123",
            "'201.555.0123',   US, +12015550123",
            "'98765 43210',    IN, +919876543210",
            "'098765-43210',   IN, +919876543210",
            "'(506) 234-5678', CA, +15062345678",
            "'07400 123456',   GB, +447400123456",
            "'020 7123 4567',  GB, +442071234567",
            "'0412 345 678',   AU, +61412345678",
            "'+61 2 9374 4000', AU, +61293744000",
    })
    void validNumbersAreNormalisedToE164(String input, String country, String expected) {
        NormalizedPhone phone = InternationalPhoneValidator.normalize(input, country);
        assertEquals(expected, phone.e164());
        assertEquals(country, phone.country());
    }

    @Test
    void usAndCanadaShareTheDialCodeButNotTheRules() {
        // 506 is a Canadian (New Brunswick) area code; 201 is a US (New Jersey) area code
        assertThrows(BadRequestException.class, () -> InternationalPhoneValidator.normalize("(506) 234-5678", "US"));
        assertThrows(BadRequestException.class, () -> InternationalPhoneValidator.normalize("(201) 555-0123", "CA"));
    }

    @ParameterizedTest
    @CsvSource({
            "'555 123',        US",   // too short
            "'(555) 010-7788', US",   // 555 is not a valid area code
            "'98765 4321',     IN",   // one digit short
            "'0740 012',       GB",   // incomplete
            "'0412 345',       AU",   // incomplete
            "'call me maybe',  US",   // letters
            "'98765 43210',    FR",   // unsupported country
    })
    void invalidNumbersAreRejected(String input, String country) {
        assertThrows(BadRequestException.class, () -> InternationalPhoneValidator.normalize(input, country));
    }

    @Test
    void withoutCountryTheInternationalPrefixIsRequired() {
        assertEquals("+447400123456", InternationalPhoneValidator.normalize("+44 7400 123456", null).e164());
        assertEquals("GB", InternationalPhoneValidator.normalize("+44 7400 123456", null).country());
        assertThrows(BadRequestException.class, () -> InternationalPhoneValidator.normalize("07400 123456", null));
    }

    @Test
    void formatsForDisplay() {
        assertEquals("+1 201-555-0123", InternationalPhoneValidator.formatInternational("+12015550123"));
        assertEquals("+91 98765 43210", InternationalPhoneValidator.formatInternational("+919876543210"));
    }
}
