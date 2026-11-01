package com.jovensbuzz.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {

    @NotBlank(message = "Display name is required")
    @Size(max = 128, message = "Display name must not exceed 128 characters")
    private String displayName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 128, message = "Email must not exceed 128 characters")
    private String email;

    public UpdateProfileRequest() {}

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
