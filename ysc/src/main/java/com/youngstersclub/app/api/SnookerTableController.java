package com.youngstersclub.app.api;

import com.youngstersclub.app.entity.SnookerTable;
import com.youngstersclub.app.repository.SnookerTableRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/snooker")
public class SnookerTableController {

    private final SnookerTableRepository repository;

    public SnookerTableController(SnookerTableRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/tables")
    public List<SnookerTable> getAvailableTables() {
        return repository.findByIsAvailable(true);
    }
}
