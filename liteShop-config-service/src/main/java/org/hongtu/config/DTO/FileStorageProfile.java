package org.hongtu.config.DTO;

import lombok.Data;

/** 不可变的文件存储配置快照，密钥以密文保存在配置库。 */
@Data
public class FileStorageProfile {
    private Long id;
    private String configJson;
}
