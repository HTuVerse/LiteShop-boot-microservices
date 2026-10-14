package org.hongtu.ai.model;

/**
 * 模型返回的标题和说明，严重程度由服务端按字段含义填写。
 *
 * @param title  标题
 * @param detail 说明
 */
public record AiTitledDetail(String title, String detail) {
}
