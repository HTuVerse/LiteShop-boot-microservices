package org.hongtu.api.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** AI 经营顾问会话。 */
@Data
@Accessors(chain = true)
public class AiAdvisorSessionDTO {
    private Long id;
    private String title;
    private String scopeType;
    private Long storeId;
    private String status;
    private String diagnosisRequestId;
    private String initialRequestId;
    private Long createdBy;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
