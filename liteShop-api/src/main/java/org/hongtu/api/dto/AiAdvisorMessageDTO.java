package org.hongtu.api.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** AI 经营顾问会话中的一轮问答。 */
@Data
@Accessors(chain = true)
public class AiAdvisorMessageDTO {
    private String requestId;
    private Long sessionId;
    private String question;
    private String responseJson;
    private String status;
    private String failureMessage;
    private String feedback;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
