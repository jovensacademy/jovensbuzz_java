package com.jovensbuzz.enquiry.entity;

import com.jovensbuzz.admin.entity.AdminUser;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "enquiries")
public class Enquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_reference", nullable = false, unique = true, length = 36)
    private String publicReference;

    @Column(name = "full_name", nullable = false, length = 128)
    private String fullName;

    // As entered in the contact form; null for enquiries submitted before V6
    @Column(name = "first_name", length = 60)
    private String firstName;

    @Column(name = "last_name", length = 60)
    private String lastName;

    @Column(nullable = false, length = 128)
    private String email;

    @Column(length = 32)
    private String phone;

    // ISO 3166-1 alpha-2 country selected for the phone number (phone itself is E.164)
    @Column(name = "phone_country", length = 2)
    private String phoneCountry;

    @Column(name = "company_name", length = 128)
    private String companyName;

    @Column(name = "service_interest", nullable = false, length = 128)
    private String serviceInterest;

    @Column(length = 64)
    private String budget;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "privacy_consent", nullable = false)
    private boolean privacyConsent = false;

    @Column(name = "privacy_policy_version", length = 32)
    private String privacyPolicyVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EnquiryStatus status = EnquiryStatus.NEW;

    @Column(nullable = false, length = 64)
    private String source = "WEBSITE_LANDING";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_admin_id")
    private AdminUser assignedAdmin;

    // SHA-256 of normalised email/service/message, used to collapse accidental resubmissions
    @Column(name = "submission_fingerprint", length = 64)
    private String submissionFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Enquiry() {}

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (status == null) status = EnquiryStatus.NEW;
        if (source == null) source = "WEBSITE_LANDING";
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPublicReference() {
        return publicReference;
    }

    public void setPublicReference(String publicReference) {
        this.publicReference = publicReference;
    }

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

    public EnquiryStatus getStatus() {
        return status;
    }

    public void setStatus(EnquiryStatus status) {
        this.status = status;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public AdminUser getAssignedAdmin() {
        return assignedAdmin;
    }

    public void setAssignedAdmin(AdminUser assignedAdmin) {
        this.assignedAdmin = assignedAdmin;
    }

    public String getSubmissionFingerprint() {
        return submissionFingerprint;
    }

    public void setSubmissionFingerprint(String submissionFingerprint) {
        this.submissionFingerprint = submissionFingerprint;
    }

    public String getPhoneCountry() {
        return phoneCountry;
    }

    public void setPhoneCountry(String phoneCountry) {
        this.phoneCountry = phoneCountry;
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
