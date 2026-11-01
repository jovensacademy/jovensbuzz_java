package com.jovensbuzz.dashboard.dto;

public class StatusDistributionDto {
    private String status;
    private long count;
    private double percentage;

    public StatusDistributionDto() {}

    public StatusDistributionDto(String status, long count, double percentage) {
        this.status = status;
        this.count = count;
        this.percentage = percentage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public double getPercentage() {
        return percentage;
    }

    public void setPercentage(double percentage) {
        this.percentage = percentage;
    }
}
