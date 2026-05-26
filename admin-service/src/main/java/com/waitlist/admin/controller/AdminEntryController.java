package com.waitlist.admin.controller;

import com.waitlist.admin.domain.Status;
import com.waitlist.admin.dto.BulkStatusRequest;
import com.waitlist.admin.service.BulkStatusService;
import com.waitlist.admin.service.EntryManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/entries")
@RequiredArgsConstructor
public class AdminEntryController {
    private final EntryManagementService entryService;
    private final BulkStatusService bulkService;
    
    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) Status status) {
        if (status != null) {
            return ResponseEntity.ok(entryService.filterByStatus(status));
        }
        return ResponseEntity.ok(entryService.listAll());
    }
    
    @PatchMapping("/{id}")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, 
                                         @RequestParam Status status,
                                         Authentication auth) {
        entryService.updateStatus(id, status, auth.getName());
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/bulk")
    public ResponseEntity<?> bulkUpdate(@Valid @RequestBody BulkStatusRequest req, Authentication auth) {
        var result = bulkService.bulkUpdate(req.getIds(), req.getNewStatus(), auth.getName());
        
        if (!result.getFailures().isEmpty()) {
            return ResponseEntity.status(207).body(result);
        }
        return ResponseEntity.ok(result);
    }
}
