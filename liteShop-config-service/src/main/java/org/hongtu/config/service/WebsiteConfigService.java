package org.hongtu.config.service;

import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.api.dto.WebsiteConfigDTO;
import org.hongtu.config.DAO.WebsiteConfigMapper;
import org.hongtu.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebsiteConfigService {
    static final String CACHE_KEY = "liteshop:website:published:v1";
    // A delayed reader/publisher must never replace a newer revision in shared Redis.
    private static final DefaultRedisScript<Long> CACHE_WRITE = new DefaultRedisScript<>("""
            local old = redis.call('GET', KEYS[1])
            if old and tonumber(cjson.decode(old).revision or 0) > tonumber(ARGV[2]) then return 0 end
            redis.call('SET', KEYS[1], ARGV[1], 'EX', 60)
            return 1
            """, Long.class);
    private final WebsiteConfigMapper mapper;
    private final StringRedisTemplate redis;

    public WebsiteConfigDTO getPublished() {
        try {
            String cached = redis.opsForValue().get(CACHE_KEY);
            if (cached != null) return JSON.parseObject(cached, WebsiteConfigDTO.class);
        } catch (RuntimeException ex) {
            log.warn("网站配置缓存不可用，回源数据库");
        }
        WebsiteConfigDTO config = getForEdit();
        cache(config);
        return config;
    }

    public WebsiteConfigDTO getForEdit() {
        String json = mapper.read();
        if (json == null) throw new BusinessException(503, "网站配置尚未初始化，请执行网站配置数据库迁移");
        return JSON.parseObject(json, WebsiteConfigDTO.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public WebsiteConfigDTO publish(WebsiteConfigDTO submitted, long userId) {
        WebsiteConfigDTO config = JSON.parseObject(JSON.toJSONString(submitted), WebsiteConfigDTO.class);
        config.setSystemName(config.getSystemName().trim());
        long expected = config.getRevision();
        config.setRevision(expected + 1);
        if (mapper.publish(JSON.toJSONString(config), expected, userId) != 1)
            throw new BusinessException(409, "配置已被其他管理员更新，请重新加载后再保存");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cache(config); }
        });
        return config;
    }

    private void cache(WebsiteConfigDTO config) {
        try {
            redis.execute(CACHE_WRITE, List.of(CACHE_KEY), JSON.toJSONString(config), config.getRevision().toString());
        } catch (RuntimeException ex) {
            // Committed database data remains authoritative; any older shared cache expires within 60s.
            log.warn("网站配置已保存，缓存同步失败，将在缓存过期后回源", ex);
        }
    }
}
