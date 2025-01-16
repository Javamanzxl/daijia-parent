package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.execption.GlobalException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.service.OrderService;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Resource
    private OrderInfoFeignClient orderInfoFeignClient;

    /**
     * 查询订单状态
     * @param orderId
     * @return
     */
    @Override
    public Integer getOrderStatus(Long orderId) {
        Result<Integer> result = orderInfoFeignClient.getOrderStatus(orderId);
        if(result.getCode()!=200){
            throw new GlobalException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }
}
