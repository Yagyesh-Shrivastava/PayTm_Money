package com.paytm.money.reservation.dto;

import java.util.List;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.validation.constraints.Max;

public class CreateShowRequest {
    @NotBlank
    public String name;

    @NotEmpty
    @Size(max = 5000)
    public List<String> seats;

    @NotNull
    @Min(0)
    public Long price_paise;

    @Min(1)
    @Max(50)
    public Integer per_user_limit = 4;
}
