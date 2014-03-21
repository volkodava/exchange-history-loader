package com.demo.exchange.xe;

import com.demo.exchange.Currency;
import org.apache.commons.lang3.Validate;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CurrencyConverter {

    private Logger logger = LoggerFactory.getLogger(getClass());

    public Currency convert(String date, Elements tableData) {
        Validate.notBlank(date, "Date must not be blank");
        Validate.notNull(tableData, "Input table data must not be null");

        String currencyCode = tableData.get(0).child(0).text().trim();
        String currencyName = tableData.get(1).text().trim();
        String strUnitsPerUSD = tableData.get(2).text().trim();
        String strUsdPerUnit = tableData.get(3).text().trim();

        double unitsPerUSD = Double.valueOf(strUnitsPerUSD);
        double usdPerUnit = Double.valueOf(strUsdPerUnit);
        Currency currency = new Currency(date, currencyCode, currencyName, strUnitsPerUSD, strUsdPerUnit);

        return currency;
    }
}
