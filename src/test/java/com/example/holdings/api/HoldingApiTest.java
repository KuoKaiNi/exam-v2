package com.example.holdings.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.holdings.quote.Market;
import com.example.holdings.quote.QuoteService;
import com.example.holdings.quote.QuoteService.Quote;
import com.example.holdings.quote.QuoteService.StockInfo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** App 1.0 API 的欄位名稱、狀態碼與錯誤格式。官方行情以 mock 取代，不連網路。 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:api-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class HoldingApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private QuoteService quoteService;

    @Test
    void 新增持股回201_欄位名稱是底線格式() throws Exception {
        when(quoteService.lookup("2330")).thenReturn(Optional.of(
                new StockInfo("2330", "台積電", Market.TWSE, new Quote(new BigDecimal("1100.00"), LocalDate.of(2026, 11, 17)))));
        when(quoteService.latestClose(anyString(), any())).thenReturn(Optional.of(
                new Quote(new BigDecimal("1100.00"), LocalDate.of(2026, 11, 17))));

        mvc.perform(post("/api/v1/holdings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"2330\",\"lots\":1,\"buy_price\":1000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("2330"))
                .andExpect(jsonPath("$.name").value("台積電"))
                .andExpect(jsonPath("$.market").value("TWSE"))
                .andExpect(jsonPath("$.lots").value(1))
                .andExpect(jsonPath("$.buy_price").value(1000.00))
                .andExpect(jsonPath("$.current_price").value(1100.00))
                .andExpect(jsonPath("$.price_date").value("2026-11-17"))
                .andExpect(jsonPath("$.pnl").value(93708))
                .andExpect(jsonPath("$.return_pct").value(9.36));
    }

    @Test
    void 重複代號回409_DUPLICATE_CODE() throws Exception {
        when(quoteService.lookup("2330")).thenReturn(Optional.of(
                new StockInfo("2330", "台積電", Market.TWSE, null)));
        mvc.perform(post("/api/v1/holdings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"2330\",\"lots\":1,\"buy_price\":1000}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/holdings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"2330\",\"lots\":2,\"buy_price\":900}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_CODE"))
                .andExpect(jsonPath("$.error.message").value("2330 已在持股中"));
    }

    @Test
    void 代號查無回404_STOCK_NOT_FOUND() throws Exception {
        when(quoteService.lookup("9999")).thenReturn(Optional.empty());

        mvc.perform(post("/api/v1/holdings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"9999\",\"lots\":1,\"buy_price\":10}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("STOCK_NOT_FOUND"));
    }

    @Test
    void 型別錯誤回400_VALIDATION_ERROR() throws Exception {
        mvc.perform(post("/api/v1/holdings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"2330\",\"lots\":1.5,\"buy_price\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/holdings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"2330\",\"lots\":\"1\",\"buy_price\":1000}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/holdings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"2330\",\"lots\":1,\"buy_price\":1000.005}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 查不到與刪除() throws Exception {
        mvc.perform(get("/api/v1/holdings/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(put("/api/v1/holdings/999999").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lots\":1,\"buy_price\":10}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/holdings/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 清單與總計欄位齊全() throws Exception {
        when(quoteService.lookup("2330")).thenReturn(Optional.of(
                new StockInfo("2330", "台積電", Market.TWSE, null)));
        mvc.perform(post("/api/v1/holdings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"2330\",\"lots\":1,\"buy_price\":1000}"));
        when(quoteService.latestClose(anyString(), any())).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/holdings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].current_price").doesNotExist())
                .andExpect(jsonPath("$[0].pnl").doesNotExist());
        mvc.perform(get("/api/v1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_cost").value(1001425))
                .andExpect(jsonPath("$.market_value").value(0))
                .andExpect(jsonPath("$.pnl").value(0))
                .andExpect(jsonPath("$.quote_missing").value(1));
    }
}
