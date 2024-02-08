package ru.practicum.shareit.item.dto;

import lombok.Builder;
import lombok.Data;
import ru.practicum.shareit.user.utils.Marker;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Builder
public class ItemRequestDto {
    private Long id;

    @NotBlank(groups = {Marker.Create.class})
    private String name;

    @NotBlank(groups = {Marker.Create.class})
    private String description;

    @NotNull(groups = {Marker.Create.class})
    private Boolean available;
}