package org.hongtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.hongtu.DAO.*;
import org.hongtu.DTO.*;
import org.hongtu.Result.Result;
import org.hongtu.exception.BusinessException;
import org.hongtu.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class UserLevelServiceImpl extends ServiceImpl<UserLevelMapper, UserLevel> implements UserLevelService {
    @Resource SysUserMapper sysUserMapper;
    @Resource UserLevelMapper userLevelMapper;
    @Resource UserLevelSchemeService schemes;

    @Override public Result listLevels() { return Result.success(baseMapper.listLevels()); }
    @Override public Result listEnabledLevels() { return Result.success(baseMapper.listEnabledLevels()); }

    private void validateInput(UserLevel level) {
        if (level == null || level.getLevelName() == null || level.getLevelName().isBlank() || level.getLevelName().length()>50)
            throw new BusinessException(400, "请输入50字以内的等级名称");
        if (level.getStatus() == null) level.setStatus(1);
        if (level.getStatus()!=0 && level.getStatus()!=1) throw new BusinessException(400,"等级状态无效");
        if (level.getSort() == null) level.setSort(0);
    }
    private UserLevel requireLevel(Long id) {
        var row = getById(id);
        if (row == null) throw new BusinessException(404,"等级不存在或不属于当前租户");
        schemes.writable(row.getSchemeId());
        return row;
    }
    private void validateChange(UserLevelScheme scheme, UserLevel changed, Long removedId) {
        var rows = new ArrayList<>(schemes.schemeLevels(scheme.getId()));
        rows.removeIf(row -> Objects.equals(row.getId(), removedId));
        if (changed != null) rows.add(changed);
        schemes.validateLevels(rows);
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public Result addLevel(UserLevel level) {
        schemes.lockTenant(); validateInput(level);
        var scheme = schemes.writable(level.getSchemeId());
        level.setId(null).setTenantId(scheme.getTenantId()).setCreateTime(LocalDateTime.now()).setUpdateTime(LocalDateTime.now());
        validateChange(scheme, level, null);
        if (!save(level)) throw new IllegalStateException("保存等级失败");
        schemes.recalculateIfActive(scheme);
        return Result.success("等级已添加", level);
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public Result updateLevel(UserLevel level) {
        schemes.lockTenant(); validateInput(level);
        var existing = requireLevel(level.getId());
        org.hongtu.security.OperationPermission.requireChange("system:level:status", existing.getStatus(), level.getStatus());
        var scheme = schemes.writable(existing.getSchemeId());
        level.setTenantId(scheme.getTenantId()).setSchemeId(scheme.getId()).setCreateTime(existing.getCreateTime()).setUpdateTime(LocalDateTime.now());
        validateChange(scheme,level,existing.getId());
        if (!updateById(level)) throw new IllegalStateException("保存等级失败");
        schemes.recalculateIfActive(scheme);
        return Result.success("等级已更新");
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public Result deleteLevel(Long id) {
        schemes.lockTenant(); var level = requireLevel(id); var scheme = schemes.writable(level.getSchemeId());
        validateChange(scheme,null,id);
        if (!removeById(id)) throw new IllegalStateException("删除等级失败");
        schemes.recalculateIfActive(scheme);
        return Result.success("等级已删除");
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public Result updateLevelStatus(Long id) {
        schemes.lockTenant(); var level = requireLevel(id); var scheme = schemes.writable(level.getSchemeId());
        level.setStatus(Integer.valueOf(1).equals(level.getStatus()) ? 0 : 1).setUpdateTime(LocalDateTime.now());
        validateChange(scheme,level,id);
        if (!updateById(level)) throw new IllegalStateException("更新等级状态失败");
        schemes.recalculateIfActive(scheme);
        return Result.success("等级状态已更新");
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public Result updateUserLevel(Long userId, Integer levelCode) {
        schemes.lockTenant(); var user = lockedUser(userId);
        var level = userLevelMapper.getLevelByLevelCode(levelCode);
        if (level == null) throw new BusinessException(400,"当前租户启用方案内没有该等级");
        user.setLevel(levelCode); user.setExp(level.getRequiredExp()); user.setUpdateTime(LocalDateTime.now());
        if (sysUserMapper.updateById(user) != 1) throw new IllegalStateException("更新会员等级失败");
        return Result.success("用户等级更新成功");
    }
    private SysUser lockedUser(Long id) {
        if (id == null) throw new BusinessException(400,"用户ID不能为空");
        var user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getId,id).last("FOR UPDATE"));
        if (user == null) throw new BusinessException(404,"用户不存在或不属于当前租户");
        return user;
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public Result increaseUserExp(Long id,Integer exp,String reason) { return changeUserExp(id,exp,reason,true); }
    @Override @Transactional(rollbackFor = Exception.class)
    public Result decreaseUserExp(Long id,Integer exp,String reason) { return changeUserExp(id,exp,reason,false); }
    public Result changeUserExp(Long id,Integer exp,String reason,boolean increase) {
        if (exp == null || exp<=0) throw new BusinessException(400,"经验值变动必须大于0");
        schemes.lockTenant(); var user = lockedUser(id);
        int oldExp=user.getExp()==null?0:user.getExp();
        long amount=increase?(long)oldExp+exp:Math.max(0L,(long)oldExp-exp);
        if (amount>Integer.MAX_VALUE) throw new BusinessException(400,"经验值超出允许范围");
        int nextExp=(int)amount; var level=userLevelMapper.getLevelByExp(nextExp);
        if(level==null) throw new BusinessException(400,"当前租户尚未配置有效的会员等级方案");
        user.setExp(nextExp); user.setLevel(level.getLevelCode()); user.setUpdateTime(LocalDateTime.now());
        if(sysUserMapper.updateById(user)!=1)throw new IllegalStateException("更新经验值失败");
        Map<String,Object> result=new HashMap<>();result.put("userId",id);result.put("oldExp",oldExp);
        result.put(increase?"addedExp":"decreasedExp",increase?exp:oldExp-nextExp);result.put("newExp",nextExp);
        result.put("reason",reason);result.put("currentLevel",level);
        return Result.success("用户经验值已更新",result);
    }
}
