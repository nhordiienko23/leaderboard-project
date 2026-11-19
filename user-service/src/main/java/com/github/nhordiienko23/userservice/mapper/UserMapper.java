package com.github.nhordiienko23.userservice.mapper;

import com.github.nhordiienko23.userservice.dto.AdminCreateUserRequest;
import com.github.nhordiienko23.userservice.dto.RegisterRequest;
import com.github.nhordiienko23.userservice.dto.UserResponse;
import com.github.nhordiienko23.userservice.entity.User;
import com.github.nhordiienko23.userservice.service.AdminUserService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "registeredAt", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toEntity(RegisterRequest request);

    UserResponse toResponse(User user);


    RegisterRequest toRegisterRequest(AdminCreateUserRequest request);
}
