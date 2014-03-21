package com.demo.exchange.core;

import com.demo.exchange.Currency;

public class CurrencyEvent {

    private final Currency currency;

    public CurrencyEvent(Currency currency) {
        this.currency = currency;
    }

    public Currency getCurrency() {
        return currency;
    }
}
