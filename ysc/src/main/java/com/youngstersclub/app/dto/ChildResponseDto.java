package com.youngstersclub.app.dto;

import java.time.LocalDate;

public class ChildResponseDto {
    private Long id;
    private String name;
    private LocalDate dateOfBirth;
    private String address;
    private String school;

    public ChildResponseDto(Long id, String name, LocalDate dateOfBirth, String address, String school) {
        this.id = id;
        this.name = name;
        this.dateOfBirth = dateOfBirth;
        this.address = address;
        this.school = school;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getAddress() {
        return address;
    }

    public String getSchool() {
        return school;
    }
}
