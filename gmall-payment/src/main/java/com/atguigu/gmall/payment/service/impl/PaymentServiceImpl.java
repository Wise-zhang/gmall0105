package com.atguigu.gmall.payment.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.atguigu.gmall.bean.PaymentInfo;
import com.atguigu.gmall.mq.ActiveMQUtil;
import com.atguigu.gmall.payment.mapper.PaymentMapper;
import com.atguigu.gmall.service.PaymentService;
import org.apache.activemq.command.ActiveMQMapMessage;
import org.apache.activemq.command.ActiveMQMessage;
import org.springframework.beans.factory.annotation.Autowired;
import tk.mybatis.mapper.entity.Example;

import javax.jms.*;

@Service
public class PaymentServiceImpl implements PaymentService {
    @Autowired
    private PaymentMapper paymentMapper;
    @Autowired
    private ActiveMQUtil activeMQUtil;

    @Override
    public void savePaymentInfo(PaymentInfo paymentInfo) {
        paymentMapper.insertSelective(paymentInfo);
    }

    @Override
    public void updatePayment(PaymentInfo paymentInfo) {
        String orderSn = paymentInfo.getOrderSn();
        Example e = new Example(PaymentInfo.class);
        e.createCriteria().andEqualTo("orderSn", orderSn);
        Connection connection = null;
        Session session = null;
        try {
            connection = activeMQUtil.getConnectionFactory().createConnection();
            session = connection.createSession(true, Session.SESSION_TRANSACTED);
        } catch (JMSException e1) {
            e1.printStackTrace();
        }
        try {
            paymentMapper.updateByExampleSelective(paymentInfo, e);
            // 支付成功，引起订单服务服务系统-》库存服务-》物流服务
            assert session != null;
            Queue payment_success_queue = session.createQueue("PAYMENT_SUCCESS_QUEUE");// 创建一个队列
            MessageProducer producer = session.createProducer(payment_success_queue); // 创建这个队列的消费者
            MapMessage mapMessage = new ActiveMQMapMessage();// hash结构

            mapMessage.setString("out_trade_no", paymentInfo.getOrderSn());
            producer.send(mapMessage);  // 往队列中添加数据
            session.commit();
        } catch (Exception ex) {
            // 消息回滚
            try {
                assert session != null;
                session.rollback();
            } catch (JMSException e1) {
                e1.printStackTrace();
            }
        } finally {
            try {
                assert connection != null;
                connection.close();
            } catch (JMSException e1) {
                e1.printStackTrace();
            }
        }
    }
}
