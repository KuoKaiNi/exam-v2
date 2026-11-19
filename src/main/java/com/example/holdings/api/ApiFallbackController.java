package com.example.holdings.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.holdings.holding.HoldingException;

/** /api/ 底下不存在的路徑也回規格的錯誤格式（404 NOT_FOUND），不回 HTML 錯誤頁。 */
@RestController
public class ApiFallbackController {

    @RequestMapping("/api/**")
    public void notFound() {
        throw new HoldingException(HoldingException.Kind.NOT_FOUND, "找不到這個 API");
    }
}
