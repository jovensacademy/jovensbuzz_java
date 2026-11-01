package com.jovensbuzz.dashboard.dto;

public class ServiceDistributionDto {
    private String service;
    private long count;
    private double percentage;

    public ServiceDistributionDto() {}

    public ServiceDistributionDto(String service, long count, double percentage) {
        this.service = service;
        this.count = count;
        this.percentage = percentage;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
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
