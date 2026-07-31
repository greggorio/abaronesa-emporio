package com.baronesa.emporio.service.payment;

import com.baronesa.emporio.entity.DeliveryOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentSyncResult {
    private DeliveryOrder order;
    private boolean becamePaid;

    public static PaymentSyncResult empty() {
        return new PaymentSyncResult(null, false);
    }
}
