package org.hongtu.restaurant.utils;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.restaurant.VO.SpecValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 规格值转换工具类
 * @author Ht
 * @since 2025-09-19
 */
@Slf4j
public class SpecValueConverter {
    
    /**
     * 将规格值列表转换为JSON字符串
     * @param specValueList 规格值列表
     * @return JSON字符串
     */
    public static String convertToString(List<SpecValue> specValueList) {
        if (specValueList == null || specValueList.isEmpty()) {
            return "[]";
        }
        
        // 处理列表中的每个规格值，设置默认值
        for (SpecValue specValue : specValueList) {
            // 如果额外价格为空，设置为0
            if (specValue.getExtraPrice() == null) {
                specValue.setExtraPrice(BigDecimal.ZERO);
            }
            
            // 如果是否默认选中为空，设置为false
            if (specValue.getIsDefault() == null) {
                specValue.setIsDefault(false);
            }
            
            // 如果排序值为空，设置为0
            if (specValue.getSortOrder() == null) {
                specValue.setSortOrder(0);
            }
        }
        
        // 使用Hutool的JSONUtil转换为JSON字符串
        return JSONUtil.toJsonStr(specValueList);
    }
    
    /**
     * 将JSON字符串转换为规格值列表
     * @param specValuesJson 规格值JSON字符串
     * @return 规格值列表
     */
    public static List<SpecValue> convertToList(String specValuesJson) {
        if (specValuesJson == null || specValuesJson.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            // 使用Hutool的JSONUtil解析JSON字符串
            return JSONUtil.toList(specValuesJson, SpecValue.class);
        } catch (Exception e) {
            try {
                // 如果解析失败，尝试解析为简单字符串数组
                JSONArray jsonArray = JSONUtil.parseArray(specValuesJson);
                List<SpecValue> specValueList = new ArrayList<>();
                
                // 转换为SpecValue对象
                for (Object value : jsonArray) {
                    SpecValue specValue = new SpecValue();
                    specValue.setValue(value.toString());
                    specValue.setExtraPrice(BigDecimal.ZERO);
                    specValue.setIsDefault(false);
                    specValue.setSortOrder(0);
                    specValue.setImageUrl(null);
                    specValueList.add(specValue);
                }
                
                return specValueList;
            } catch (Exception ex) {
                log.error("解析规格值失败: {}", e.getMessage());
                return new ArrayList<>();
            }
        }
    }
} 