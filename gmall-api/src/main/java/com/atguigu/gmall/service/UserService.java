package com.atguigu.gmall.service;

import com.atguigu.gmall.bean.UmsMember;
import com.atguigu.gmall.bean.UmsMemberReceiveAddress;

import java.util.List;

public interface UserService {
    List<UmsMember> getAllUser();

    List<UmsMemberReceiveAddress> getReceiveAddressByMemberId(String memberId);

    UmsMember login(UmsMember umsMember);

    void addUserToken(String token, String memberId);


    UmsMember checkOauthUser(UmsMember umsMember);

    UmsMember addOauthUser(UmsMember umsMember);

    UmsMemberReceiveAddress getReceiveAddressById(String receiveAddressId);
}
