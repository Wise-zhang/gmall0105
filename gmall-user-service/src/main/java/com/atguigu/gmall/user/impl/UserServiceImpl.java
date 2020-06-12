package com.atguigu.gmall.user.impl;


import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.UmsMember;
import com.atguigu.gmall.bean.UmsMemberReceiveAddress;
import com.atguigu.gmall.service.UserService;
import com.atguigu.gmall.user.mapper.UmsMemberReceiveAddressMapper;
import com.atguigu.gmall.user.mapper.UserMapper;
import com.atguigu.gmall.util.RedisUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    UserMapper userMapper;

    @Autowired
    UmsMemberReceiveAddressMapper umsMemberReceiveAddressMapper;

    @Autowired
    RedisUtil redisUtil;

    @Override
    public List<UmsMember> getAllUser() {

        return userMapper.selectAll();
    }

    @Override
    public List<UmsMemberReceiveAddress> getReceiveAddressByMemberId(String memberId) {

        // 封装的参数对象
        UmsMemberReceiveAddress umsMemberReceiveAddress = new UmsMemberReceiveAddress();
        umsMemberReceiveAddress.setMemberId(memberId);
        return umsMemberReceiveAddressMapper.select(umsMemberReceiveAddress);
    }


    @Override
    public UmsMember login(UmsMember umsMember) {
        Jedis jedis;
        jedis = redisUtil.getJedis();

        if (jedis != null) {
            String umsMemberStr = jedis.get("user:" + umsMember.getPassword() + ":info");

            if (StringUtils.isNotBlank(umsMemberStr)) {
                // 密码正确
                return JSON.parseObject(umsMemberStr, UmsMember.class);
            }
        }
        // 链接redis失败，开启数据库
        UmsMember umsMemberFromDb = loginFromDb(umsMember);
        if (umsMemberFromDb != null) {
            assert jedis != null;
            jedis.setex("user:" + umsMember.getPassword() + ":info", 60 * 60 * 24, JSON.toJSONString(umsMemberFromDb));
        }
        return umsMemberFromDb;
    }

    @Override
    public void addUserToken(String token, String memberId) {
        Jedis jedis = redisUtil.getJedis();

        jedis.setex("user:" + memberId + ":token", 60 * 60 * 2, token);

        jedis.close();
    }

    private UmsMember loginFromDb(UmsMember umsMember) {

        List<UmsMember> umsMembers = userMapper.select(umsMember);

        if (umsMembers != null) {
            return umsMembers.get(0);
        }

        return null;

    }

    @Override
    public UmsMember checkOauthUser(UmsMember umsMember) {
        return userMapper.selectOne(umsMember);
    }

    @Override
    public UmsMember addOauthUser(UmsMember umsMember) {
        userMapper.insertSelective(umsMember);
        return umsMember;
    }

    @Override
    public UmsMemberReceiveAddress getReceiveAddressById(String receiveAddressId) {
        UmsMemberReceiveAddress umsMemberReceiveAddress = new UmsMemberReceiveAddress();
        umsMemberReceiveAddress.setId(receiveAddressId);

        return umsMemberReceiveAddressMapper.selectOne(umsMemberReceiveAddress);
    }
}
