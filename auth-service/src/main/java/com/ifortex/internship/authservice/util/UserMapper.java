package com.ifortex.internship.authservice.util;

import com.ifortex.internship.authservice.model.Account;
import com.ifortex.internship.authserviceapi.dto.request.UpdateUserDto;
import com.ifortex.internship.authserviceapi.dto.response.ClientDto;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "twoFactorEnabled", target = "isTwoFactorEnabled")
    ClientDto userToClientDto(Account account);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(source = "isTwoFactorEnabled", target = "twoFactorEnabled")
    void updateAccountFromDto(UpdateUserDto dto, @MappingTarget Account account);
}
