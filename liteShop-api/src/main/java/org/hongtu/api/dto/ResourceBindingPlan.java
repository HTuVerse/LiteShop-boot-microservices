package org.hongtu.api.dto;

import java.util.List;

/** 预登记结果；旧引用ID用于提交后精确清理，避免并发保存误删新引用。 */
public record ResourceBindingPlan(ResourceBindingRequest request, List<ResourceBinding> bindings,
                                  List<Long> previousReferenceIds) { }
