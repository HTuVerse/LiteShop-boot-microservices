package org.hongtu.restaurant.VO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hongtu.restaurant.DTO.TableReservation;

/** 带桌位展示信息的预约记录。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TableReservationVO extends TableReservation {
    private String tableNumber;
    private String tableName;
    private String storeName;
    private String statusDesc;
}
