package org.hongtu.api.dto;

import java.util.List;

/** 业务一次保存涉及的资源字段与目标位置。 */
public record ResourceBindingRequest(Long tenantId, String businessType, Long businessId,
                                     List<String> fields, List<ResourceBinding> bindings) { }
