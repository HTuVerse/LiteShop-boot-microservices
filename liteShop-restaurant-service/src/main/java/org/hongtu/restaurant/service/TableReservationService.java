package org.hongtu.restaurant.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.restaurant.DTO.TableReservation;
import org.hongtu.restaurant.DTO.TableReservationRequest;
import org.hongtu.restaurant.VO.TableReservationVO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

public interface TableReservationService extends IService<TableReservation> {
    Page<TableReservationVO> getPage(Long storeId, Long tableId, String phone, Integer status, String statusGroup,
                                     LocalDateTime startTime, LocalDateTime endTime,
                                     Integer currentPage, Integer pageSize);
    TableReservationVO createReservation(TableReservationRequest request, Long operatorId);
    TableReservationVO updateReservation(TableReservationRequest request);
    boolean cancelReservation(Long id, String reason);
    boolean markNoShow(Long id);
    void markArrived(Long reservationId, Long tableId, String sessionNo);
    void completeBySessionNo(String sessionNo);
    void completeBySessionNos(Collection<String> sessionNos);
    Map<Long, TableReservation> findNextByTableIds(Collection<Long> tableIds);
}
