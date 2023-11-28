package ru.practicum.shareit.user.dto;

import lombok.Builder;
import lombok.Data;
import ru.practicum.shareit.user.utils.Marker;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;

@Builder
@Data
public class UserDto {

    private long id;

    @NotBlank(groups = {Marker.Create.class})
    private String name;

    @Email(groups = {Marker.Update.class, Marker.Create.class})
    @NotEmpty(groups = {Marker.Create.class})
    private String email;
}