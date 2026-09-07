package org.hongtu.service;

import lombok.RequiredArgsConstructor;
import org.hongtu.Result.Result;
import org.hongtu.api.client.ConfigFeignClient;
import org.hongtu.api.dto.WebsiteConfigDTO;
import org.hongtu.exception.BusinessException;
import org.hongtu.utils.RateLimitUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** 登录策略由配置服务提供，验证码和失败计数均在服务端执行。 */
@Service
@RequiredArgsConstructor
public class AdminLoginSecurity {
    private static final String PREFIX = "liteshop:admin-login:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> FAILURE = new DefaultRedisScript<>("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('EXPIRE', KEYS[1], 900) end
            if n >= tonumber(ARGV[1]) then
              redis.call('SET', KEYS[2], '1', 'EX', ARGV[2], 'NX')
              redis.call('DEL', KEYS[1])
              return 1
            end
            return 0
            """, Long.class);
    private final ConfigFeignClient configClient;
    private final StringRedisTemplate redis;
    private final RateLimitUtil rateLimit;

    public WebsiteConfigDTO policy() {
        try {
            Result<WebsiteConfigDTO> response = configClient.website();
            if (response != null && Result.CODE_SUCCESS.equals(response.getCode()) && response.getData() != null)
                return response.getData();
        } catch (RuntimeException ignored) { }
        throw new BusinessException(503, "暂时无法读取登录配置，请稍后重试");
    }

    public void requireOpenForMerchant() {
        WebsiteConfigDTO c = policy();
        if (Boolean.TRUE.equals(c.getMaintenanceEnabled()))
            throw new BusinessException(503, c.getMaintenanceMessage());
    }

    /** Consumer login cannot be used to obtain an admin session while website controls are active. */
    public void requireCustomerLoginAllowed(List<String> roles) {
        if (roles == null || roles.stream().allMatch("USER"::equals)) return;
        WebsiteConfigDTO c = policy();
        if (Boolean.TRUE.equals(c.getCaptchaEnabled()) || c.getMaxLoginAttempts() != null
                || Boolean.TRUE.equals(c.getMaintenanceEnabled()))
            throw new BusinessException(403, "管理账号请从管理端登录入口完成安全验证");
    }

    public Result<?> authenticate(String account, String captchaId, String captchaCode, Supplier<Result> action) {
        WebsiteConfigDTO c = policy();
        String key = PREFIX + DigestUtils.md5DigestAsHex(account.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        try {
            if (c.getMaxLoginAttempts() != null && Boolean.TRUE.equals(redis.hasKey(key + ":locked")))
                throw new BusinessException(429, "登录失败次数过多，请在锁定时间结束后重试");
            if (Boolean.TRUE.equals(c.getCaptchaEnabled())) verifyCaptcha(captchaId, captchaCode);
            Result<?> result = action.get();
            if (Result.CODE_SUCCESS.equals(result.getCode())) {
                redis.delete(key + ":failures");
            } else if (c.getMaxLoginAttempts() != null && !Integer.valueOf(503).equals(result.getCode())) {
                Long locked = redis.execute(FAILURE, List.of(key + ":failures", key + ":locked"),
                        c.getMaxLoginAttempts().toString(), Integer.toString(c.getLockMinutes() * 60));
                if (Long.valueOf(1).equals(locked))
                    return Result.error(429, "登录失败次数已达上限，账号已锁定 " + c.getLockMinutes() + " 分钟");
            }
            return result;
        } catch (BusinessException ex) {
            throw ex;
        } catch (org.springframework.data.redis.RedisConnectionFailureException ex) {
            throw new BusinessException(503, "登录安全校验暂不可用，请稍后重试");
        }
    }

    public Map<String, Object> captcha(String remoteAddress) {
        if (!Boolean.TRUE.equals(policy().getCaptchaEnabled())) return Map.of("enabled", false);
        if (!rateLimit.isAllowed("admin-captcha:" + remoteAddress, 20, 60))
            throw new BusinessException(429, "验证码获取过于频繁，请稍后再试");
        String alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 4; i++) code.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        BufferedImage image = new BufferedImage(150, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(245, 247, 252));
            g.fillRect(0, 0, 150, 48);
            for (int i = 0; i < 12; i++) {
                g.setColor(new Color(120 + RANDOM.nextInt(120), 120 + RANDOM.nextInt(120), 120 + RANDOM.nextInt(120)));
                g.drawLine(RANDOM.nextInt(150), RANDOM.nextInt(48), RANDOM.nextInt(150), RANDOM.nextInt(48));
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            for (int i = 0; i < 4; i++) {
                g.setColor(new Color(RANDOM.nextInt(100), RANDOM.nextInt(100), RANDOM.nextInt(120)));
                g.drawString(code.substring(i, i + 1), 15 + i * 32, 33 + RANDOM.nextInt(6));
            }
        } finally { g.dispose(); }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            String id = UUID.randomUUID().toString().replace("-", "");
            redis.opsForValue().set(PREFIX + "captcha:" + id, code.toString(), Duration.ofMinutes(2));
            return Map.of("enabled", true, "captchaId", id, "image", "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray()));
        } catch (java.io.IOException ex) {
            throw new BusinessException(503, "验证码生成失败，请重试");
        }
    }

    void verifyCaptcha(String id, String code) {
        if (id == null || !id.matches("[a-f0-9]{32}") || code == null || code.isBlank())
            throw new BusinessException(400, "请填写图片验证码");
        String expected = redis.opsForValue().getAndDelete(PREFIX + "captcha:" + id);
        if (expected == null || !expected.equalsIgnoreCase(code.trim()))
            throw new BusinessException(400, "图片验证码错误或已过期，请刷新后重试");
    }
}
