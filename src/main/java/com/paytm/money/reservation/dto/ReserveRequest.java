package com.paytm.money.reservation.dto;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class ReserveRequest {
    @NotEmpty
    public List<String> seats;

    @NotBlank
    public String idempotency_key;
}
