package org.hongtu.restaurant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.hongtu.restaurant.DTO.DishSpecOption;
import org.hongtu.restaurant.DAO.DishSpecOptionMapper;
import org.hongtu.restaurant.service.DishSpecOptionService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.hongtu.restaurant.utils.SpecValueConverter;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 菜品规格选项表 服务实现类
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Service
public class DishSpecOptionServiceImpl extends ServiceImpl<DishSpecOptionMapper, DishSpecOption> implements DishSpecOptionService {

    @Override
    public List<DishSpecOption> selectSpecOptionsByDishId(Long dishId){
        LambdaQueryWrapper<DishSpecOption> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DishSpecOption::getDishId, dishId);
        queryWrapper.orderByAsc(DishSpecOption::getSortOrder);  // 按排序值升序排序

        // 查询规格选项列表
        List<DishSpecOption> dishSpecOptions = baseMapper.selectList(queryWrapper);

        // 为每个规格选项设置规格值列表
        for (DishSpecOption dishSpecOption : dishSpecOptions) {
            dishSpecOption.setSpecValueList(SpecValueConverter.convertToList(dishSpecOption.getSpecValues()));
        }

        return dishSpecOptions;
    }
}
