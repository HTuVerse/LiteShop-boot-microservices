package org.hongtu.restaurant.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.restaurant.DTO.LocalOutbox;

/** 餐厅本地发件箱。 */
@Mapper
public interface LocalOutboxMapper extends BaseMapper<LocalOutbox> {
}
