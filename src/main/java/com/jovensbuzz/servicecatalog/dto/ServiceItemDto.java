package com.jovensbuzz.servicecatalog.dto;

import com.jovensbuzz.servicecatalog.entity.ServiceItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ServiceItemDto {
    private Long id;

    @NotBlank(message = "Service title is required")
    @Size(max = 128, message = "Title must not exceed 128 characters")
    private String title;

    @NotBlank(message = "Slug is required")
    @Size(max = 128)
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug may contain lowercase letters, numbers and single hyphens")
    private String slug;

    @NotBlank(message = "Description is required")
    private String description;

    private String icon;
    private int displayOrder;
    private boolean published = true;

    public ServiceItemDto() {}

    public static ServiceItemDto fromEntity(ServiceItem item) {
        ServiceItemDto dto = new ServiceItemDto();
        dto.setId(item.getId());
        dto.setTitle(item.getTitle());
        dto.setSlug(item.getSlug());
        dto.setDescription(item.getDescription());
        dto.setIcon(item.getIcon());
        dto.setDisplayOrder(item.getDisplayOrder());
        dto.setPublished(item.isPublished());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }
}
