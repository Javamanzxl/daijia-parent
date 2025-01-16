package com.atguigu.daijia.order.service.impl;

import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.entity.order.OrderStatusLog;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import com.atguigu.daijia.order.mapper.OrderInfoMapper;
import com.atguigu.daijia.order.mapper.OrderStatusLogMapper;
import com.atguigu.daijia.order.service.OrderInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

@Service
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {

    @Resource
    private OrderInfoMapper orderInfoMapper;
    @Resource
    private OrderStatusLogMapper orderStatusLogMapper;

    /**
     * 保存订单信息
     * @param orderInfoForm
     * @return
     */
    @Override
    public Long saveOrderInfo(OrderInfoForm orderInfoForm) {
        OrderInfo orderInfo = new OrderInfo();
        BeanUtils.copyProperties(orderInfoForm,orderInfo);
        String orderNo = UUID.randomUUID().toString().replace("-", "");
        //设置订单号
        orderInfo.setOrderNo(orderNo);
        //设置订单状态
        orderInfo.setStatus(OrderStatus.WAITING_ACCEPT.getStatus());
        orderInfoMapper.insert(orderInfo);
        //修改订单状态日志表
        this.log(orderInfo.getId(),orderInfo.getStatus());
        return orderInfo.getId();
    }

    /**
     * 修改订单状态日志表
     * @param orderId
     * @param status
     */
    public void log(Long orderId, Integer status) {
        OrderStatusLog orderStatusLog = new OrderStatusLog();
        orderStatusLog.setOrderId(orderId);
        orderStatusLog.setOrderStatus(status);
        orderStatusLog.setOperateTime(new Date());
        orderStatusLogMapper.insert(orderStatusLog);
    }

    /**
     * 根据订单id获取订单状态
     * @param orderId
     * @return
     */
    @Override
    public Integer getOrderStatus(Long orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if(orderInfo == null) {
            //返回null，feign解析会抛出异常，给默认值，后续会用
            return OrderStatus.NULL_ORDER.getStatus();
        }
        return orderInfo.getStatus();
    }
}
