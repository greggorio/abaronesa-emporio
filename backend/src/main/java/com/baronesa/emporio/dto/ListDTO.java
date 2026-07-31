package com.baronesa.emporio.dto;

import java.util.List;

import lombok.Data;

@Data
public class ListDTO {
    private Long id;
    private String name;
    private String type;
    private String created_by;
    private List<TaskDTO> tasks;
}