package org.hongtu.saas.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.AiGenerationDTO;
import org.hongtu.api.dto.AiAdvisorMessageDTO;
import org.hongtu.api.dto.AiAdvisorSessionDTO;
import org.hongtu.api.dto.AiPageDTO;
import org.hongtu.saas.service.SaasAiGenerationService;
import org.hongtu.saas.service.SaasAiWorkspaceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 店长的内部接口：生成额度预占/完成/失败回退，以及经营顾问会话工作区。
 * 仅供其它服务通过 Feign 调用，路径沿用 /inner/saas 以保持调用方零感知。
 */
@RestController
@RequestMapping("/inner/saas")
@SaIgnore
public class InnerAiController {
    @Resource private SaasAiGenerationService aiGenerations;
    @Resource private SaasAiWorkspaceService aiWorkspace;

    /** 原子预占 AI 店长的一次周期调用额度。 */
    @PostMapping("/ai-generation/reserve") public Result<AiGenerationDTO> reserveAi(@RequestBody AiGenerationDTO dto) {
        return Result.success(aiGenerations.reserve(dto));
    }
    /** 保存已生成结果和 Token 用量，供幂等重试复用。 */
    @PostMapping("/ai-generation/complete") public Result<AiGenerationDTO> completeAi(@RequestBody AiGenerationDTO dto) {
        return Result.success(aiGenerations.complete(dto));
    }
    /** 模型失败后按原周期退回预占额度。 */
    @PostMapping("/ai-generation/fail") public Result<AiGenerationDTO> failAi(@RequestBody AiGenerationDTO dto) {
        return Result.success(aiGenerations.fail(dto));
    }
    /** 查询当前租户指定经营周期的 AI 店长生成记录。 */
    @GetMapping("/ai-generation/records")
    public Result<List<AiGenerationDTO>> aiRecords(@RequestParam String scopeType,
                                                    @RequestParam(required = false) Long storeId,
                                                    @RequestParam String timeRange) {
        return Result.success(aiGenerations.records(scopeType, storeId, timeRange));
    }

    /** 查询当前租户指定经营周期的 AI 经营诊断仪表盘记录（含失败与进行中）。 */
    @GetMapping("/ai-generation/diagnosis-records")
    public Result<List<AiGenerationDTO>> aiDiagnosisRecords(@RequestParam String scopeType,
                                                            @RequestParam(required = false) Long storeId,
                                                            @RequestParam String timeRange) {
        return Result.success(aiGenerations.diagnosisRecords(scopeType, storeId, timeRange));
    }

    /** 新建、重命名或归档经营顾问会话。 */
    @PostMapping("/ai-workspace/session")
    public Result<AiAdvisorSessionDTO> saveAdvisorSession(@RequestBody AiAdvisorSessionDTO dto) {
        return Result.success(aiWorkspace.saveSession(dto));
    }

    /** 分页查询当前用户的经营顾问会话。 */
    @GetMapping("/ai-workspace/sessions")
    public Result<AiPageDTO<AiAdvisorSessionDTO>> advisorSessions(
            @RequestParam Long createdBy, @RequestParam Integer currentPage,
            @RequestParam Integer pageSize, @RequestParam(required = false) String status) {
        return Result.success(aiWorkspace.sessions(createdBy, currentPage, pageSize, status));
    }

    /** 恢复会话问答历史。 */
    @GetMapping("/ai-workspace/sessions/{id}/messages")
    public Result<List<AiAdvisorMessageDTO>> advisorMessages(@PathVariable Long id, @RequestParam Long createdBy) {
        return Result.success(aiWorkspace.messages(id, createdBy));
    }

    /** 保存 AI 建议有用性反馈。 */
    @PostMapping("/ai-workspace/feedback")
    public Result<AiGenerationDTO> feedback(@RequestBody AiGenerationDTO dto) {
        return Result.success(aiWorkspace.feedback(dto));
    }
}
