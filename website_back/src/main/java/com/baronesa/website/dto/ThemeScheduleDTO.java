package com.baronesa.website.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThemeScheduleDTO {

    private Long themeId;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private Integer priority;
}