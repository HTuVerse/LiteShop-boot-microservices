package org.hongtu.api.dto;

/** 业务字段中的一个资源位置；外部 URL 的 resourceId 为空。 */
public record ResourceBinding(String fieldName, Integer sortOrder, Long resourceId, String url) { }
