package com.atguigu.daijia.order.service.impl;

import com.atguigu.daijia.model.entity.order.OrderMonitor;
import com.atguigu.daijia.model.entity.order.OrderMonitorRecord;
import com.atguigu.daijia.order.mapper.OrderMonitorMapper;
import com.atguigu.daijia.order.repository.OrderMonitorRecordRepository;
import com.atguigu.daijia.order.service.OrderMonitorService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderMonitorServiceImpl extends ServiceImpl<OrderMonitorMapper, OrderMonitor> implements OrderMonitorService {

    @Resource
    private OrderMonitorRecordRepository orderMonitorRecordRepository;
    @Resource
    private OrderMonitorMapper orderMonitorMapper;

    /**
     * 存订单监控记录数据到MongoDB
     * @param orderMonitorRecord
     * @return
     */
    @Override
    public Boolean saveOrderMonitorRecord(OrderMonitorRecord orderMonitorRecord) {
        orderMonitorRecordRepository.save(orderMonitorRecord);
        return true;
    }

    /**
     * 根据订单id获取订单监控信息
     * @param orderId
     * @return
     */
    @Override
    public OrderMonitor getOrderMonitor(Long orderId) {

        return orderMonitorMapper.selectOne(new LambdaQueryWrapper<OrderMonitor>()
                .eq(OrderMonitor::getOrderId, orderId));
    }

    /**
     * 更新订单监控信息
     * @param orderMonitor
     * @return
     */
    @Override
    public Boolean updateOrderMonitor(OrderMonitor orderMonitor) {
        int i = orderMonitorMapper.updateById(orderMonitor);
        return i == 1;
    }

    /**
     * 保存订单监控信息
     * @param orderMonitor
     */
    @Override
    public void saveOrderMonitor(OrderMonitor orderMonitor) {
        orderMonitorMapper.insert(orderMonitor);
    }
}
