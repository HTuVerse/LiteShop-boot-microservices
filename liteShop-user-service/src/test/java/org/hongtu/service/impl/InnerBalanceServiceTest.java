package org.hongtu.service.impl;

import org.hongtu.DAO.SurplusRecordMapper;
import org.hongtu.DAO.SysUserMapper;
import org.hongtu.DTO.SysUser;
import org.hongtu.DTO.UserSurplusRecord;
import org.hongtu.api.dto.BalanceChangeDTO;
import org.hongtu.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InnerBalanceServiceTest {
    @Test void expenseUsesAtomicDeductionAndRecordsExactBalances() {
        var users = mock(SysUserMapper.class);
        var records = mock(SurplusRecordMapper.class);
        var service = new InnerBalanceService();
        ReflectionTestUtils.setField(service, "sysUserMapper", users);
        ReflectionTestUtils.setField(service, "surplusRecordMapper", records);

        when(users.deductBalance(8L, new BigDecimal("20.00"))).thenReturn(1);
        var user = new SysUser();
        user.setId(8L);
        user.setSurplusMoney(new BigDecimal("80.00"));
        when(users.selectById(8L)).thenReturn(user);

        var balance = service.changeBalance(BalanceChangeDTO.builder()
                .userId(8L).amount(new BigDecimal("20.00")).type(4).orderNo("ORDER-1").build());

        assertEquals(new BigDecimal("80.00"), balance);
        var captor = ArgumentCaptor.forClass(UserSurplusRecord.class);
        verify(records).insert(captor.capture());
        assertEquals(new BigDecimal("100.00"), captor.getValue().getOldBalance());
        assertEquals(new BigDecimal("80.00"), captor.getValue().getNewBalance());
        verify(users, never()).updateById(any(SysUser.class));
    }

    @Test void insufficientBalanceNeverWritesARecord() {
        var users = mock(SysUserMapper.class);
        var records = mock(SurplusRecordMapper.class);
        var service = new InnerBalanceService();
        ReflectionTestUtils.setField(service, "sysUserMapper", users);
        ReflectionTestUtils.setField(service, "surplusRecordMapper", records);
        var user = new SysUser();
        user.setId(8L);
        when(users.selectById(8L)).thenReturn(user);

        var request = BalanceChangeDTO.builder()
                .userId(8L).amount(new BigDecimal("20.00")).type(4).orderNo("ORDER-2").build();
        assertThrows(BusinessException.class, () -> service.changeBalance(request));
        verify(records, never()).insert(any(UserSurplusRecord.class));
    }
}
