package com.claimsphere.common.domain;

/**
 * The six APAC markets served by the platform, each with its local settlement currency.
 */
public enum Market {
    AUSTRALIA(CurrencyCode.AUD),
    SINGAPORE(CurrencyCode.SGD),
    HONG_KONG(CurrencyCode.HKD),
    INDIA(CurrencyCode.INR),
    MALAYSIA(CurrencyCode.MYR),
    THAILAND(CurrencyCode.THB);

    private final CurrencyCode localCurrency;

    Market(CurrencyCode localCurrency) {
        this.localCurrency = localCurrency;
    }

    public CurrencyCode localCurrency() {
        return localCurrency;
    }
}
