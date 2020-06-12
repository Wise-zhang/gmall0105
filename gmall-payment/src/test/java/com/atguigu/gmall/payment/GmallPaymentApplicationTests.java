package com.atguigu.gmall.payment;

import com.atguigu.gmall.mq.ActiveMQUtil;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GmallPaymentApplicationTests {

    @Autowired
    ActiveMQUtil activeMQUtil;

    @Test
    public void contextLoads() throws JMSException {
        String s = "123";
        System.out.println(StringUtils.isNumeric(s));

    }

}
