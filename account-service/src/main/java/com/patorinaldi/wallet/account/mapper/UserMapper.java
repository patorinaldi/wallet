package com.patorinaldi.wallet.account.mapper;

import com.patorinaldi.wallet.account.dto.UserResponse;
import com.patorinaldi.wallet.account.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);

}
