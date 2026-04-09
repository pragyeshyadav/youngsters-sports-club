package com.youngstersclub.app.api;

import com.youngstersclub.app.entity.SnookerTable;
import com.youngstersclub.app.repository.SnookerTableRepository;
import com.youngstersclub.app.service.FrameService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/snooker")
public class SnookerTableController {

    private final SnookerTableRepository repository;
    private final FrameService frameService;

    public SnookerTableController(SnookerTableRepository repository, FrameService frameService) {
        this.repository = repository;
        this.frameService = frameService;
    }

    @GetMapping("/tables")
    public List<SnookerTable> getAvailableTables() {
        return repository.findAvailableTablesSafe();
    }

    @GetMapping("/tables/status")
    public List<Map<String, Object>> getTableStatuses() {
        return frameService.getAllTableStatuses();
    }
}
