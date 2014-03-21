package com.demo.exchange;

public class Currency {

    private String date;
    private String code;
    private String name;
    private String unitsPerUsd;
    private String usdPerUnit;

    public Currency(String date, String code, String name, String unitsPerUsd, String usdPerUnit) {
        this.date = date;
        this.code = code;
        this.name = name;
        this.unitsPerUsd = unitsPerUsd;
        this.usdPerUnit = usdPerUnit;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUnitsPerUsd() {
        return unitsPerUsd;
    }

    public void setUnitsPerUsd(String unitsPerUsd) {
        this.unitsPerUsd = unitsPerUsd;
    }

    public String getUsdPerUnit() {
        return usdPerUnit;
    }

    public void setUsdPerUnit(String usdPerUnit) {
        this.usdPerUnit = usdPerUnit;
    }

    @Override
    public String toString() {
        return date + ": code=" + code + ", name=" + name + ", unitsPerUsd=" + unitsPerUsd + ", usdPerUnit=" + usdPerUnit;
    }

}
