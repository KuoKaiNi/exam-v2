package com.example.holdings.holding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.holdings.support.IntegrationTestBase;

class HoldingServiceTest extends IntegrationTestBase {

    @Autowired
    HoldingService service;

    @Test
    void createUpdateDelete() {
        Holding h = service.create("2330", "1", "1000");
        assertThat(service.list()).hasSize(1);

        service.update(h.getId(), "2", "980.5");
        Holding updated = service.get(h.getId());
        assertThat(updated.getLots()).isEqualTo(2);
        assertThat(updated.getBuyPrice()).isEqualByComparingTo("980.50");

        service.delete(h.getId());
        assertThat(service.list()).isEmpty();
    }

    @Test
    void duplicateCodeIsRejectedAndNotSaved() {
        service.create("2330", "1", "1000");
        assertThatThrownBy(() -> service.create(" 2330 ", "5", "900"))
                .isInstanceOf(HoldingException.class)
                .hasMessage("2330 已在持股中")
                .extracting(e -> ((HoldingException) e).getKind())
                .isEqualTo(HoldingException.Kind.DUPLICATE_CODE);
        assertThat(service.list()).hasSize(1);
        assertThat(service.list().get(0).getLots()).isEqualTo(1);
    }

    @Test
    void invalidUpdateKeepsOldValues() {
        Holding h = service.create("2330", "1", "1000");
        assertThatThrownBy(() -> service.update(h.getId(), "1000", "1")).isInstanceOf(HoldingException.class);
        assertThat(service.get(h.getId()).getLots()).isEqualTo(1);
    }

    @Test
    void unknownCodeIsRejectedAndNotSaved() {
        assertThatThrownBy(() -> service.create("9999", "1", "10"))
                .hasMessageContaining("9999")
                .extracting(e -> ((HoldingException) e).getKind())
                .isEqualTo(HoldingException.Kind.STOCK_NOT_FOUND);
        assertThat(service.list()).isEmpty();
    }

    @Test
    void missingIdIsNotFound() {
        assertThatThrownBy(() -> service.delete(9999))
                .extracting(e -> ((HoldingException) e).getKind())
                .isEqualTo(HoldingException.Kind.NOT_FOUND);
    }
}
