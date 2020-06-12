package com.atguigu.gmall.cart.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.OmsCartItem;
import com.atguigu.gmall.cart.mapper.OmsCartItemMapper;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;
import tk.mybatis.mapper.entity.Example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Service
public class CartServiceImpl implements CartService {
    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private OmsCartItemMapper omsCartItemMapper;

    @Override
    public OmsCartItem ifCartExistByUser(String memberId, String skuId) {
        OmsCartItem cartItem = new OmsCartItem();
        cartItem.setMemberId(memberId);
        cartItem.setProductSkuId(skuId);
        return omsCartItemMapper.selectOne(cartItem);
    }

    @Override
    public void addCart(OmsCartItem omsCartItem) {
        omsCartItemMapper.insertSelective(omsCartItem);  // 只将不为空的添加到数据库
    }

    @Override
    public void updateCart(OmsCartItem omsCartItem) {
        Example e = new Example(OmsCartItem.class);
        e.createCriteria().andEqualTo("id", omsCartItem.getId());
        omsCartItemMapper.updateByExampleSelective(omsCartItem, e); // 只将omsCartItem中不为空的字段添加进数据库
    }

    @Override
    public void flushCartCache(String memberId) {
        OmsCartItem omsCartItem = new OmsCartItem();
        omsCartItem.setMemberId(memberId);
        List<OmsCartItem> omsCartItemList = omsCartItemMapper.select(omsCartItem);

        // 同步到redis缓存中
        Jedis jedis = redisUtil.getJedis();

        HashMap<String, String> map = new HashMap<>();
        for (OmsCartItem cartItem : omsCartItemList) {
            cartItem.setTotalPrice(cartItem.getPrice().multiply(cartItem.getQuantity()));
            map.put(cartItem.getProductId(), JSON.toJSONString(cartItem));
        }
        jedis.del("user:" + memberId + ":cart");
        jedis.hmset("user:" + memberId + ":cart", map);
        jedis.close();
    }

    @Override
    public List<OmsCartItem> cartList(String memberId) {
        Jedis jedis = null;
        List<OmsCartItem> omsCartItems = new ArrayList<>();

        try {
            jedis = redisUtil.getJedis();
            List<String> hvals = jedis.hvals("user:" + memberId + ":cart");
            for (String hval : hvals) {
                OmsCartItem cartItem = JSON.parseObject(hval, OmsCartItem.class);
                omsCartItems.add(cartItem);
            }
        } catch (Exception e) {
            // 处理异常，记录系统日志
            e.printStackTrace();
            //String message = e.getMessage();
            //logService.addErrLog(message);
            return omsCartItems;
        }

        return omsCartItems;
    }

    @Override
    public void checkCart(OmsCartItem omsCartItem) {
        // 更新数据库
        Example e = new Example(OmsCartItem.class);
        e.createCriteria().andEqualTo("memberId", omsCartItem.getMemberId()).andEqualTo("productSkuId", omsCartItem.getProductSkuId());
        omsCartItemMapper.updateByExampleSelective(omsCartItem, e);
        // 缓存同步
        flushCartCache(omsCartItem.getMemberId());
    }
}
