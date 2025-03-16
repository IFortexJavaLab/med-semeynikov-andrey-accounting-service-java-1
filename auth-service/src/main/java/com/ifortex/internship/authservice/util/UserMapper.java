package com.ifortex.internship.authservice.util;

import com.ifortex.internship.authservice.dto.request.UpdateAccountDto;
import com.ifortex.internship.authservice.model.Account;
import com.ifortex.internship.authservice.model.Role;
import com.ifortex.internship.authserviceapi.dto.response.AccountDto;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "twoFactorEnabled", target = "isTwoFactorEnabled")
    @Mapping(target = "role", source = "role", qualifiedByName = "roleToUserRole")
    AccountDto userToClientDto(Account account);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(source = "isTwoFactorEnabled", target = "twoFactorEnabled")
    void updateAccountFromDto(UpdateAccountDto dto, @MappingTarget Account account);

    @Named("roleToUserRole")
    static String roleToUserRole(Role role) {
        return role != null ? role.getName().name() : null;
    }
}
