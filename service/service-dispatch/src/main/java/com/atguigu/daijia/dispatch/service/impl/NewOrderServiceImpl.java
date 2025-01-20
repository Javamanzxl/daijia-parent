package com.atguigu.daijia.dispatch.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.dispatch.mapper.OrderJobMapper;
import com.atguigu.daijia.dispatch.service.NewOrderService;
import com.atguigu.daijia.dispatch.xxl.client.XxlJobClient;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.model.entity.dispatch.OrderJob;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.map.SearchNearByDriverForm;
import com.atguigu.daijia.model.vo.dispatch.NewOrderTaskVo;
import com.atguigu.daijia.model.vo.map.NearByDriverVo;
import com.atguigu.daijia.model.vo.order.NewOrderDataVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.statement.select.KSQLWindow;
import org.springframework.beans.BeanUtils;
import org.springframework.cglib.proxy.Dispatcher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class NewOrderServiceImpl implements NewOrderService {
    @Resource
    private XxlJobClient xxlJobClient;
    @Resource
    private OrderJobMapper orderJobMapper;
    @Resource
    private LocationFeignClient locationFeignClient;
    @Resource
    private OrderInfoFeignClient orderInfoFeignClient;
    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    /**
     * 添加并开始新订单任务调度
     * @param newOrderTaskVo
     * @return
     */
    @Override
    public Long addAndStartTask(NewOrderTaskVo newOrderTaskVo) {
        //1.判断当前订单是否启动任务调度
        OrderJob orderJob = orderJobMapper.selectOne(new LambdaQueryWrapper<OrderJob>()
                .eq(OrderJob::getOrderId, newOrderTaskVo.getOrderId()));
        //2.没有启动，进行操作
        if(orderJob == null){
            //创建并启动任务调度
            /**
             * String executorHandler 执行任务job方法
             * String param
             * String corn 执行的corn表达式
             * String desc 描述信息
             */
            Long jobId = xxlJobClient.addAndStart("newOrderTaskHandler", "", "0 0/1 * * * ?", "新订单任务,订单id：" + newOrderTaskVo.getOrderId());
            //记录任务调度信息
            orderJob = new OrderJob();
            orderJob.setJobId(jobId);
            orderJob.setOrderId(newOrderTaskVo.getOrderId());
            orderJob.setParameter(JSONObject.toJSONString(newOrderTaskVo));
            orderJobMapper.insert(orderJob);
        }
        return orderJob.getJobId();
    }

    /**
     * 执行任务，搜索附近代驾司机
     * @param jobId
     */
    @Override
    public void executeTask(long jobId) {
        //1.根据jobId查询数据库，当前任务是否已经创建
        //如果没有创建，不执行
        OrderJob orderJob = orderJobMapper.selectOne(new LambdaQueryWrapper<OrderJob>()
                .eq(OrderJob::getJobId, jobId));
        if(orderJob==null){
            return;
        }
        //2.查询订单状态，如果当前订单接单状态，继续执行。如果当前订单不是接单状态，停止任务调度
        String jsonString = orderJob.getParameter();
        NewOrderTaskVo newOrderTaskVo = JSONObject.parseObject(jsonString, NewOrderTaskVo.class);
        Long orderId = newOrderTaskVo.getOrderId();
        Integer status = orderInfoFeignClient.getOrderStatus(orderId).getData();
        if(status != OrderStatus.WAITING_ACCEPT.getStatus().intValue()){
            xxlJobClient.stopJob(jobId);
            log.info("停止任务调度: {}", JSONObject.toJSONString(newOrderTaskVo));
            return;
        }
        //3.远程调用，搜索附近满足条件可以接单的司机
        //4.远程调用后，得到可以接单司机的集合
        SearchNearByDriverForm searchNearByDriverForm = new SearchNearByDriverForm();
        searchNearByDriverForm.setLongitude(newOrderTaskVo.getStartPointLongitude());
        searchNearByDriverForm.setLatitude(newOrderTaskVo.getStartPointLatitude());
        searchNearByDriverForm.setMileageDistance(newOrderTaskVo.getExpectDistance());
        List<NearByDriverVo> drivers = locationFeignClient.searchNearByDriver(searchNearByDriverForm).getData();
        //5.遍历司机集合，得到每个司机，为每个司机创建临时队列，存储新订单信息
        drivers.forEach(driver->{
            //使用Redis的Set类型
            //记录司机id，防止重复推送订单信息
            String repeatKey = RedisConstant.DRIVER_ORDER_REPEAT_LIST+newOrderTaskVo.getOrderId();
            Boolean isMember = redisTemplate.opsForSet().isMember(repeatKey, driver.getDriverId());
            if(Boolean.FALSE.equals(isMember)){
                //把订单信息推送给满足条件的司机
                redisTemplate.opsForSet().add(repeatKey,driver.getDriverId());
                //设置过期时间：15分钟，超过15分钟没有接单自动取消
                redisTemplate.expire(repeatKey,RedisConstant.DRIVER_ORDER_REPEAT_LIST_EXPIRES_TIME, TimeUnit.MINUTES);
                //新订单保存到司机的临时队列中，Redis里面的List集合
                NewOrderDataVo newOrderDataVo = new NewOrderDataVo();
                BeanUtils.copyProperties(newOrderTaskVo,newOrderDataVo);
                //将消息保存到司机的临时队列里面，司机接单了会定时轮询到他的临时队列获取订单消息
                String key = RedisConstant.DRIVER_ORDER_TEMP_LIST+driver.getDriverId();
                redisTemplate.opsForList().leftPush(key,JSONObject.toJSONString(newOrderDataVo));
                //过期时间：1分钟，1分钟未消费，自动过期
                //注：司机端开启接单，前端每5秒（远小于1分钟）拉取1次“司机临时队列”里面的新订单消息
                redisTemplate.expire(key, RedisConstant.DRIVER_ORDER_TEMP_LIST_EXPIRES_TIME, TimeUnit.MINUTES);
            }
        });
    }

    /**
     * 查询司机新订单数据
     * @param driverId
     * @return
     */
    @Override
    public List<NewOrderDataVo> findNewOrderQueueData(Long driverId) {
        List<NewOrderDataVo> list = new ArrayList<>();
        String key = RedisConstant.DRIVER_ORDER_TEMP_LIST + driverId;
        Long size = redisTemplate.opsForList().size(key);
        if(size>0){
            for (int i = 0; i < size; i++) {
                String content = (String)redisTemplate.opsForList().leftPop(key);
                NewOrderDataVo newOrderDataVo = JSONObject.parseObject(content, NewOrderDataVo.class);
                list.add(newOrderDataVo);
            }
        }
        return list;
    }

    /**
     * 清空新订单队列数据
     * @param driverId
     * @return
     */
    @Override
    public Boolean clearNewOrderQueueData(Long driverId) {
        String key = RedisConstant.DRIVER_ORDER_TEMP_LIST + driverId;
        //直接删除，司机开启服务后，有新订单会自动创建容器
        redisTemplate.delete(key);
        return true;
    }
}
