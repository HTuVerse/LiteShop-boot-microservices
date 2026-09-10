package org.hongtu.announcement.dto;

import java.util.List;

/** 与管理端现有分页数据结构一致。 */
public record PageResult<T>(List<T> records, long total, long current, long size) {
}
