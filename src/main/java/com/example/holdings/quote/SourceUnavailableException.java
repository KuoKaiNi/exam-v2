package com.example.holdings.quote;

/** 官方來源連不上、逾時、或回應格式不對。跟「查無此代號」是不同的情況。 */
public class SourceUnavailableException extends RuntimeException {

    public SourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public SourceUnavailableException(String message) {
        super(message);
    }
}
