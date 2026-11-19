package com.example.holdings.holding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class HoldingInputTest {

    @Test
    void acceptsValidValues() {
        HoldingInput.Values v = HoldingInput.parseValues(" 999 ", "1066.67");
        assertThat(v.lots()).isEqualTo(999);
        assertThat(v.buyPrice()).isEqualByComparingTo(new BigDecimal("1066.67"));
        assertThat(HoldingInput.parseValues("1", "0.01").buyPrice()).isEqualByComparingTo("0.01");
        assertThat(HoldingInput.parseValues("1", "10.500").buyPrice()).isEqualByComparingTo("10.5");
    }

    @Test
    void normalizesCode() {
        assertThat(HoldingInput.parseCode(" 00632r ")).isEqualTo("00632R");
        assertThat(HoldingInput.parseCode("2330")).isEqualTo("2330");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "23", "2330!", "1234567", "台積電"})
    void rejectsBadCode(String code) {
        assertThatThrownBy(() -> HoldingInput.parseCode(code)).isInstanceOf(HoldingException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1000", "-1", "1.5", "abc", "", "1e2"})
    void rejectsBadLots(String lots) {
        assertThatThrownBy(() -> HoldingInput.parseValues(lots, "10"))
                .isInstanceOf(HoldingException.class)
                .extracting(e -> ((HoldingException) e).getKind())
                .isEqualTo(HoldingException.Kind.VALIDATION_ERROR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-5", "10.123", "abc", "", "1,000"})
    void rejectsBadPrice(String price) {
        assertThatThrownBy(() -> HoldingInput.parseValues("1", price)).isInstanceOf(HoldingException.class);
    }

    @Test
    void reportsBothErrorsAtOnce() {
        assertThatThrownBy(() -> HoldingInput.parseValues("0", "-1"))
                .hasMessageContaining("張數").hasMessageContaining("買進價");
    }
}
