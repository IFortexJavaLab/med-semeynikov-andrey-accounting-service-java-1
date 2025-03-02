package com.ifortex.internship.authservice.util;

import com.ifortex.internship.authservice.model.Role;
import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authserviceapi.dto.AuthUserDto;
import com.ifortex.internship.authserviceapi.dto.request.UpdateUserDto;
import com.ifortex.internship.authserviceapi.dto.response.ClientDto;
import com.ifortex.internship.authserviceapi.dto.response.UserListViewDto;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "roles", target = "roles", qualifiedByName = "mapRolesToStrings")
    @Mapping(source = "twoFactorEnabled", target = "isTwoFactorEnabled")
    ClientDto userToClientDto(User user);

    @Mapping(source = "roles", target = "roles", qualifiedByName = "mapRolesToStrings")
    AuthUserDto userToAuthUserDto(User user);

    @Mapping(source = "roles", target = "roles", qualifiedByName = "mapRolesToStrings")
    UserListViewDto userToUserListViewDto(User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(source = "isTwoFactorEnabled", target = "twoFactorEnabled")
    void updateUserFromDto(UpdateUserDto dto, @MappingTarget User user);

    @Named("mapRolesToStrings")
    default List<String> mapRolesToStrings(List<Role> roles) {
        return roles.stream()
            .map(role -> role.getName().name())
            .toList();
    }
}
