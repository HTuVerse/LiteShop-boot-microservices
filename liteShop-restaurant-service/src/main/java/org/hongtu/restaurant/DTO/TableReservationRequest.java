package org.hongtu.restaurant.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 新增或编辑餐桌预约请求。 */
@Data
public class TableReservationRequest {
    private Long id;
    private Long tableId;
    private String reservationPhone;
    private Integer peopleCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime expectedArrivalTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime expectedEndTime;
    private String reservationRemark;
}
