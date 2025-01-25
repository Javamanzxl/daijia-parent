package com.atguigu.daijia.order.listener;

import com.atguigu.daijia.order.service.OrderInfoService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @author ：zxl
 * @Description: Redission实现延迟队列的监听器
 * @ClassName: OrderCancelListener
 * @date ：2025/01/25 20:41
 */
@Component
public class OrderCancelListener {
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private OrderInfoService orderInfoService;

    @PostConstruct
    public void OrderCancel(){
        new Thread(()->{
            while(true){
                //1.获取延迟队列里面阻塞队列
                RBlockingQueue<String> queueCancel = redissonClient.getBlockingQueue("queue_cancel");
                //2.从队列中获取消息
                try {
                    String orderId = queueCancel.take();
                    if(StringUtils.hasText(orderId)){
                        //调用方法取消订单
                        orderInfoService.orderCancel(Long.parseLong(orderId));
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }
}
