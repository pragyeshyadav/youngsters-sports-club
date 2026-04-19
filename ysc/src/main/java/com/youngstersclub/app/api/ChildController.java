package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.ChildRequest;
import com.youngstersclub.app.dto.ChildResponseDto;
import com.youngstersclub.app.service.ChildService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/children")
public class ChildController {

    private final ChildService childService;

    public ChildController(ChildService childService) {
        this.childService = childService;
    }

    @PostMapping
    public ResponseEntity<ChildResponseDto> addChild(@RequestBody ChildRequest request) {
        return ResponseEntity.ok(childService.addChild(request));
    }

    @GetMapping("/by-parent")
    public ResponseEntity<List<ChildResponseDto>> getByParent(@RequestParam Integer parentUserId) {
        return ResponseEntity.ok(childService.getChildrenByParent(parentUserId));
    }
}
