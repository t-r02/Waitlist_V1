package com.waitlist.admin.dto;

import com.waitlist.admin.domain.Status;
import lombok.Data;
import java.util.List;

@Data
public class BulkStatusRequest {
    private List<Long> ids;
    private Status newStatus;
}
