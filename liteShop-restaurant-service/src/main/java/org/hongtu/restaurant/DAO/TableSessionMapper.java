package org.hongtu.restaurant.DAO;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.hongtu.restaurant.DTO.Dish;
import org.hongtu.restaurant.DTO.TableSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 餐桌会话表 Mapper 接口
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Mapper
public interface TableSessionMapper extends BaseMapper<TableSession> {

    /**
     * 根据会话编号查询餐桌会话
     *
     * @param sessionNo 会话编号
     * @return 餐桌会话信息
     */
    @Select("SELECT * FROM table_session WHERE session_no = #{sessionNo}")
    TableSession selectBySessionNo(String sessionNo);

}
