package com.baronesa.emporio.dto.response;

import java.util.List;

import com.baronesa.emporio.entity.MPPayment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MPSearchPaymentResponse {

    private List<MPPayment> results;

    private MPPaging paging;
}