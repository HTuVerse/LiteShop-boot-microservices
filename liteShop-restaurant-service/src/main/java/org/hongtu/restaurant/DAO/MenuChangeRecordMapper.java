package org.hongtu.restaurant.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.restaurant.DTO.MenuChangeRecord;

/**
 * 菜单变更记录数据访问接口。
 */
@Mapper
public interface MenuChangeRecordMapper extends BaseMapper<MenuChangeRecord> {
}
