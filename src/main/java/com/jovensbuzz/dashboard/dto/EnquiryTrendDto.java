package com.jovensbuzz.dashboard.dto;

public class EnquiryTrendDto {
    private String label;
    private long count;
    private String date;

    public EnquiryTrendDto() {}

    public EnquiryTrendDto(String label, long count) {
        this.label = label;
        this.count = count;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
}
