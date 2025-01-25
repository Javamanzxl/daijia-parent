package com.atguigu.daijia.common.service;


import jakarta.annotation.Resource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class RabbitService {
    @Resource
    private RabbitTemplate rabbitTemplate;
    public boolean sendMessage(String exchange,
                               String routingKey,
                               Object message) {
        rabbitTemplate.convertAndSend(exchange,routingKey,message);
        return true;
    }

}
