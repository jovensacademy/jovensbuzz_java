package com.jovensbuzz.dashboard.dto;

import com.jovensbuzz.enquiry.dto.EnquiryResponseDto;
import java.util.List;

public class DashboardSummaryDto {
    private long totalEnquiries;
    private long newEnquiries;
    private long contactedEnquiries;
    private long qualifiedLeads;
    private long convertedLeads;
    private long closedEnquiries;
    private long enquiriesToday;
    private long enquiriesThisMonth;
    private double conversionRate;
    private List<EnquiryResponseDto> recentEnquiries;
    private long spamEnquiries;
    private long syncFailed;
    private long syncPending;

    public DashboardSummaryDto() {}

    public long getTotalEnquiries() {
        return totalEnquiries;
    }

    public void setTotalEnquiries(long totalEnquiries) {
        this.totalEnquiries = totalEnquiries;
    }

    public long getNewEnquiries() {
        return newEnquiries;
    }

    public void setNewEnquiries(long newEnquiries) {
        this.newEnquiries = newEnquiries;
    }

    public long getContactedEnquiries() {
        return contactedEnquiries;
    }

    public void setContactedEnquiries(long contactedEnquiries) {
        this.contactedEnquiries = contactedEnquiries;
    }

    public long getQualifiedLeads() {
        return qualifiedLeads;
    }

    public void setQualifiedLeads(long qualifiedLeads) {
        this.qualifiedLeads = qualifiedLeads;
    }

    public long getConvertedLeads() {
        return convertedLeads;
    }

    public void setConvertedLeads(long convertedLeads) {
        this.convertedLeads = convertedLeads;
    }

    public long getClosedEnquiries() {
        return closedEnquiries;
    }

    public void setClosedEnquiries(long closedEnquiries) {
        this.closedEnquiries = closedEnquiries;
    }

    public long getEnquiriesToday() {
        return enquiriesToday;
    }

    public void setEnquiriesToday(long enquiriesToday) {
        this.enquiriesToday = enquiriesToday;
    }

    public long getEnquiriesThisMonth() {
        return enquiriesThisMonth;
    }

    public void setEnquiriesThisMonth(long enquiriesThisMonth) {
        this.enquiriesThisMonth = enquiriesThisMonth;
    }

    public double getConversionRate() {
        return conversionRate;
    }

    public void setConversionRate(double conversionRate) {
        this.conversionRate = conversionRate;
    }

    public List<EnquiryResponseDto> getRecentEnquiries() {
        return recentEnquiries;
    }

    public void setRecentEnquiries(List<EnquiryResponseDto> recentEnquiries) {
        this.recentEnquiries = recentEnquiries;
    }

    public long getSpamEnquiries() {
        return spamEnquiries;
    }

    public void setSpamEnquiries(long spamEnquiries) {
        this.spamEnquiries = spamEnquiries;
    }

    public long getSyncFailed() {
        return syncFailed;
    }

    public void setSyncFailed(long syncFailed) {
        this.syncFailed = syncFailed;
    }

    public long getSyncPending() {
        return syncPending;
    }

    public void setSyncPending(long syncPending) {
        this.syncPending = syncPending;
    }
}
