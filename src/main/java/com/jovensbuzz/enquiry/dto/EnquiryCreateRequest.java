package com.jovensbuzz.enquiry.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class EnquiryCreateRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 128, message = "Name must be between 2 and 128 characters")
    private String fullName;

    // Optional: sent by the contact form so the spreadsheet can keep the names separate
    @Size(max = 60, message = "First name must not exceed 60 characters")
    private String firstName;

    @Size(max = 60, message = "Last name must not exceed 60 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 128, message = "Email must not exceed 128 characters")
    private String email;

    // International format with country code, e.g. +1 959 200 0495
    @NotBlank(message = "Phone number is required")
    @Size(max = 32, message = "Phone must not exceed 32 characters")
    @Pattern(regexp = "^\\+[1-9][0-9 ()\\-]{6,30}$", message = "Enter a phone number with country code, e.g. +1 555 123 4567")
    private String phone;

    // ISO country chosen in the form (US, IN, CA, GB, AU); decides the validation rules.
    // Optional for older clients, in which case the +country code in phone is used.
    @Pattern(regexp = "^[A-Za-z]{2}$", message = "Invalid phone country")
    private String phoneCountry;

    // Published service ID from GET /api/v1/services (preferred over serviceInterest)
    private Long serviceId;

    @Size(max = 128, message = "Company name must not exceed 128 characters")
    private String companyName;

    // Title of the service; optional when serviceId is supplied
    @Size(max = 128, message = "Service interest must not exceed 128 characters")
    private String serviceInterest;

    @Size(max = 64, message = "Budget must not exceed 64 characters")
    private String budget;

    @NotBlank(message = "Please tell us a little about your project")
    @Size(min = 10, max = 4000, message = "Message must be between 10 and 4000 characters")
    private String message;

    // Optional client consent flag: when absent/false, direct enquiry legitimate interest applies
    private boolean privacyConsent = false;

    // Optional privacy policy version in effect at the time of submission (e.g. "2026-10")
    @Size(max = 32, message = "Privacy policy version must not exceed 32 characters")
    private String privacyPolicyVersion;

    // Client hint only; the service whitelists accepted values
    @Size(max = 64)
    private String source = "WEBSITE_LANDING";

    // Honeypot: hidden from humans, so any value means an automated submission
    @Size(max = 256)
    private String website;

    public EnquiryCreateRequest() {}

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getServiceInterest() {
        return serviceInterest;
    }

    public void setServiceInterest(String serviceInterest) {
        this.serviceInterest = serviceInterest;
    }

    public String getBudget() {
        return budget;
    }

    public void setBudget(String budget) {
        this.budget = budget;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isPrivacyConsent() {
        return privacyConsent;
    }

    public void setPrivacyConsent(boolean privacyConsent) {
        this.privacyConsent = privacyConsent;
    }

    public String getPrivacyPolicyVersion() {
        return privacyPolicyVersion;
    }

    public void setPrivacyPolicyVersion(String privacyPolicyVersion) {
        this.privacyPolicyVersion = privacyPolicyVersion;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getPhoneCountry() {
        return phoneCountry;
    }

    public void setPhoneCountry(String phoneCountry) {
        this.phoneCountry = phoneCountry;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
}
