package org.hongtu.api.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 跨服务使用的轻量分页结果，避免接口层依赖 MyBatis 分页类型。 */
@Data
@Accessors(chain = true)
public class AiPageDTO<T> {
    private List<T> records;
    private long total;
    private long current;
    private long size;
}
