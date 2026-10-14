package org.hongtu.restaurant.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.restaurant.DTO.PrintJob;

/** 打印任务数据访问层。 */
@Mapper
public interface PrintJobMapper extends BaseMapper<PrintJob> {
}
