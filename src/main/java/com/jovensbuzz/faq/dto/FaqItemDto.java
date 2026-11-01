package com.jovensbuzz.faq.dto;

import com.jovensbuzz.faq.entity.FaqItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FaqItemDto {
    private Long id;

    @NotBlank(message = "Question is required")
    @Size(max = 255, message = "Question must not exceed 255 characters")
    private String question;

    @NotBlank(message = "Answer is required")
    @Size(max = 3000, message = "Answer must not exceed 3000 characters")
    private String answer;

    @Min(0)
    @Max(9999)
    private int displayOrder;
    private boolean published = true;

    public FaqItemDto() {}

    public static FaqItemDto fromEntity(FaqItem item) {
        FaqItemDto dto = new FaqItemDto();
        dto.setId(item.getId());
        dto.setQuestion(item.getQuestion());
        dto.setAnswer(item.getAnswer());
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

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
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
