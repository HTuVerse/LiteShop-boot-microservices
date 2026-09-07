package org.hongtu.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.hongtu.DAO.FileUploadMapper;
import org.hongtu.DTO.FileUpload;
import org.hongtu.service.FileUploadService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 文件上传 服务实现类
 * </p>
 *
 * @author Honghong
 * @since 2025-07-06
 */
@Service
public class FileUploadServiceImpl extends ServiceImpl<FileUploadMapper, FileUpload> implements FileUploadService {

}
