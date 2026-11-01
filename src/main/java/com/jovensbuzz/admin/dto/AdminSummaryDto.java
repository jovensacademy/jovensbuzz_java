package com.jovensbuzz.admin.dto;

import com.jovensbuzz.admin.entity.AdminUser;

/**
 * Minimal administrator identity used for assignment pickers.
 */
public class AdminSummaryDto {

    private Long id;
    private String username;
    private String displayName;

    public AdminSummaryDto() {}

    public static AdminSummaryDto fromEntity(AdminUser admin) {
        AdminSummaryDto dto = new AdminSummaryDto();
        dto.setId(admin.getId());
        dto.setUsername(admin.getUsername());
        dto.setDisplayName(admin.getDisplayName());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
