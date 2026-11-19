package com.example.holdings.quote;

/** 官方網站連不上、逾時、被擋，或回應不是預期的格式。跟「查無此代號」不同。 */
public class QuoteSourceException extends Exception {

    public QuoteSourceException(String message) {
        super(message);
    }

    public QuoteSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
