package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.ChildRequest;
import com.youngstersclub.app.dto.ChildResponseDto;
import com.youngstersclub.app.entity.Child;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.repository.ChildRepository;
import com.youngstersclub.app.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChildService {

    private static final long MAX_CHILDREN_PER_PARENT = 10;

    private final ChildRepository childRepository;
    private final UserRepository userRepository;

    public ChildService(ChildRepository childRepository, UserRepository userRepository) {
        this.childRepository = childRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ChildResponseDto addChild(ChildRequest request) {
        if (request == null || request.getParentUserId() == null) {
            throw new IllegalArgumentException("Parent is required");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Child name is required");
        }
        if (request.getDateOfBirth() == null || request.getDateOfBirth().trim().isEmpty()) {
            throw new IllegalArgumentException("Date of birth is required");
        }

        long currentCount = childRepository.countByParentUser_Id(request.getParentUserId());
        if (currentCount >= MAX_CHILDREN_PER_PARENT) {
            throw new IllegalArgumentException("Maximum 10 children allowed per parent");
        }

        User parent = userRepository.findById(request.getParentUserId()).orElseThrow();
        Child child = new Child();
        child.setParentUser(parent);
        child.setName(request.getName().trim());
        child.setDateOfBirth(LocalDate.parse(request.getDateOfBirth().trim()));
        child.setAddress(request.getAddress() == null ? null : request.getAddress().trim());
        child.setSchool(request.getSchool() == null ? null : request.getSchool().trim());

        Child saved = childRepository.save(child);
        return toDto(saved);
    }

    public List<ChildResponseDto> getChildrenByParent(Integer parentUserId) {
        if (parentUserId == null) {
            return List.of();
        }
        return childRepository.findByParentUser_IdOrderByCreatedAtDesc(parentUserId).stream()
                .map(this::toDto)
                .toList();
    }

    public Child getOwnedChild(Long childId, Integer parentUserId) {
        Child child = childRepository.findById(childId).orElseThrow();
        if (child.getParentUser() == null || child.getParentUser().getId() == null
                || !child.getParentUser().getId().equals(parentUserId)) {
            throw new IllegalArgumentException("Child does not belong to this parent");
        }
        return child;
    }

    private ChildResponseDto toDto(Child child) {
        return new ChildResponseDto(
                child.getId(),
                child.getName(),
                child.getDateOfBirth(),
                child.getAddress(),
                child.getSchool());
    }
}
