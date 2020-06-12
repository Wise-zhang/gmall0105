package com.atguigu.gmall.order.service.impl;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.atguigu.gmall.bean.OmsOrder;
import com.atguigu.gmall.bean.OmsOrderItem;
import com.atguigu.gmall.mq.ActiveMQUtil;
import com.atguigu.gmall.order.mapper.OmsOrderItemMapper;
import com.atguigu.gmall.order.mapper.OmsOrderMapper;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.service.OrderService;
import com.atguigu.gmall.util.RedisUtil;
import org.apache.activemq.command.ActiveMQMapMessage;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;
import tk.mybatis.mapper.entity.Example;

import javax.jms.*;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OmsOrderItemMapper omsOrderItemMapper;

    @Autowired
    private OmsOrderMapper omsOrderMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Reference
    private CartService cartService;

    @Autowired
    private ActiveMQUtil activeMQUtil;

    @Override
    public String genTradeCode(String memberId) {
        Jedis jedis = redisUtil.getJedis();
        String tradeKey = "user:" + memberId + ":tradeCode";
        String tradeCode = UUID.randomUUID().toString();
        jedis.setex(tradeKey, 60 * 15, tradeCode);
        jedis.close();
        return tradeCode;
    }

    @Override
    public String checkTradeCode(String memberId, String tradeCode) {
        String flag = "fail";
        Jedis jedis = redisUtil.getJedis();
        String tradeKey = "user:" + memberId + ":tradeCode";
        // 使用lua脚本查到就立即删除，避免高并发出错；如果KEYS[1]) == ARGV[1]，删除键，否则返回0
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        Long res = (Long) jedis.eval(script, Collections.singletonList(tradeKey), Collections.singletonList(tradeCode)); // 第二个参数给KEYS[1]；第三个参数给ARGV[1]
        if (res != null && res != 0) {
            flag = "success";
        }
        return flag;
    }

    @Override
    public void saveOrder(OmsOrder omsOrder) {
        // 保存订单表
        omsOrderMapper.insertSelective(omsOrder);

        String orderId = omsOrder.getId();
        // 保存订单详情
        List<OmsOrderItem> omsOrderItems = omsOrder.getOmsOrderItems();
        for (OmsOrderItem omsOrderItem : omsOrderItems) {
            omsOrderItem.setOrderId(orderId);
            omsOrderItemMapper.insertSelective(omsOrderItem);
            //删除购物车
//            cartService.delCart();
        }
    }

    @Override
    public OmsOrder getOrderByOutTradeNo(String outTradeNo) {
        OmsOrder omsOrder = new OmsOrder();
        omsOrder.setOrderSn(outTradeNo);
        return omsOrderMapper.selectOne(omsOrder);
    }

    @Override
    public void updateOrder(OmsOrder omsOrder) {
        Example e = new Example(OmsOrder.class);
        e.createCriteria().andEqualTo("orderSn", omsOrder.getOrderSn());

        OmsOrder omsOrderUpdate = new OmsOrder();
        omsOrderUpdate.setStatus(1);

        // 发送一个订单已支付的队列，提供给库存消费
        Connection connection = null;
        Session session = null;
        try {
            omsOrderMapper.updateByExampleSelective(omsOrderUpdate, e);
            connection = activeMQUtil.getConnectionFactory().createConnection();
            session = connection.createSession(true, Session.SESSION_TRANSACTED);
            Queue payhment_success_queue = session.createQueue("ORDER_PAY_QUEUE");
            MessageProducer producer = session.createProducer(payhment_success_queue);
            MapMessage mapMessage = new ActiveMQMapMessage();// hash结构
            producer.send(mapMessage);
            session.commit();

        } catch (Exception ex) {
            // 消息回滚
            try {
                session.rollback();
            } catch (JMSException e1) {
                e1.printStackTrace();
            }
        } finally {
            try {
                session.close();
                connection.close();
            } catch (JMSException e1) {
                e1.printStackTrace();
            }
        }
    }
}
