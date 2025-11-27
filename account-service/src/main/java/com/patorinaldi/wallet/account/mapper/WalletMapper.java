package com.patorinaldi.wallet.account.mapper;

import com.patorinaldi.wallet.account.dto.WalletResponse;
import com.patorinaldi.wallet.account.entity.Wallet;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WalletMapper {

    @Mapping(source = "id", target = "walletId")
    @Mapping(source = "user.id", target = "userId")
    WalletResponse toResponse(Wallet wallet);

}