package com.waitlist.admin.dto;

import com.waitlist.admin.domain.Status;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BulkStatusRequest {

    @NotEmpty(message = "ids must not be empty")
    @Size(max = 500, message = "ids must contain 500 entries or fewer")
    private List<Long> ids;

    @NotNull(message = "newStatus is required")
    private Status newStatus;
}
