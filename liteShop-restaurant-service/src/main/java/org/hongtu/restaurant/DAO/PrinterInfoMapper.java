package org.hongtu.restaurant.DAO;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.hongtu.restaurant.DTO.PrinterInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
 * <p>
 * 飞鹅打印机信息表 Mapper 接口
 * </p>
 *
 * @author Ht
 * @since 2025-01-26
 */
@Mapper
public interface PrinterInfoMapper extends BaseMapper<PrinterInfo> {


    /**
     * 根据门店ID查询打印机列表
     *
     * @param storeId 门店ID
     * @return 打印机列表
     */
    @Select("SELECT * FROM printer_info WHERE store_id = #{storeId} ORDER BY is_default DESC")
    List<PrinterInfo> selectByStoreId(@Param("storeId") Long storeId);

    /**
     * 根据打印机编号查询
     *
     * @param sn 打印机编号
     * @return 打印机信息
     */
    @Select("SELECT * FROM printer_info WHERE sn = #{sn}")
    PrinterInfo selectBySn(@Param("sn") String sn);

}
