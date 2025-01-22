package com.atguigu.daijia.map.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.util.LocationUtil;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.map.repository.OrderServiceLocationRepository;
import com.atguigu.daijia.map.service.LocationService;
import com.atguigu.daijia.model.entity.driver.DriverSet;
import com.atguigu.daijia.model.entity.map.OrderServiceLocation;
import com.atguigu.daijia.model.form.map.OrderServiceLocationForm;
import com.atguigu.daijia.model.form.map.SearchNearByDriverForm;
import com.atguigu.daijia.model.form.map.UpdateDriverLocationForm;
import com.atguigu.daijia.model.form.map.UpdateOrderLocationForm;
import com.atguigu.daijia.model.vo.map.NearByDriverVo;
import com.atguigu.daijia.model.vo.map.OrderLocationVo;
import com.atguigu.daijia.model.vo.map.OrderServiceLastLocationVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

@Service
public class LocationServiceImpl implements LocationService {
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private DriverInfoFeignClient driverInfoFeignClient;
    @Resource
    private OrderServiceLocationRepository orderServiceLocationRepository;
    @Resource
    private MongoTemplate mongoTemplate;


    /**
     * 开启接单服务：更新司机经纬度位置
     *
     * @param updateDriverLocationForm
     * @return
     */
    @Override
    public Boolean updateDriverLocation(UpdateDriverLocationForm updateDriverLocationForm) {
        Point point = new Point(updateDriverLocationForm.getLongitude().doubleValue()
                , updateDriverLocationForm.getLatitude().doubleValue());
        redisTemplate.opsForGeo().add(RedisConstant.DRIVER_GEO_LOCATION, point
                , updateDriverLocationForm.getDriverId().toString());
        return true;
    }

    /**
     * 关闭接单服务：删除司机经纬度位置
     *
     * @param driverId
     * @return
     */
    @Override
    public Boolean removeDriverLocation(Long driverId) {
        redisTemplate.opsForGeo().remove(RedisConstant.DRIVER_GEO_LOCATION, driverId.toString());
        return true;
    }

    /**
     * 搜索附近满足条件的司机
     *
     * @param searchNearByDriverForm
     * @return
     */
    @Override
    public List<NearByDriverVo> searchNearByDriver(SearchNearByDriverForm searchNearByDriverForm) {
        //搜索5公里以内的司机
        //1.使用redis.geo查询司机
        //1.1 定义经纬度点
        Point point = new Point(searchNearByDriverForm.getLongitude().doubleValue(), searchNearByDriverForm.getLatitude().doubleValue());
        //1.2 定义距离：5公里(系统配置)
        Distance distance = new Distance(SystemConstant.NEARBY_DRIVER_RADIUS, RedisGeoCommands.DistanceUnit.KILOMETERS);
        //1.3 定义以point点为中心，distance为距离这么一个范围
        Circle circle = new Circle(point, distance);
        //1.4 定义GEO参数,设置返回结果包含的内容
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance()  //包含距离
                .includeCoordinates()   //包含坐标
                .sortAscending();   //升序排列
        GeoResults<RedisGeoCommands.GeoLocation<Object>> geoResults = redisTemplate.opsForGeo().radius(RedisConstant.DRIVER_GEO_LOCATION, circle, args);
        //2.查询redis最终返回list
        if(geoResults!=null){
            List<GeoResult<RedisGeoCommands.GeoLocation<Object>>> content = geoResults.getContent();
            List<NearByDriverVo> nearByDrivers = new ArrayList<>();
            //3.对list集合进行处理
            //遍历list集合，得到每个司机信息,根据每个司机的个性化信息判断
            for (GeoResult<RedisGeoCommands.GeoLocation<Object>> item : content) {
                Long driverId = Long.parseLong(item.getContent().getName().toString());
                //当前距离
                BigDecimal currentDistance = BigDecimal.valueOf(item.getDistance().getValue()).setScale(2, RoundingMode.HALF_UP);
                //获取司机接单设置参数
                DriverSet driverSet = driverInfoFeignClient.getDriverSet(driverId).getData();
                //接单里程判断，acceptDistance==0：不限制，
                if (driverSet.getAcceptDistance().doubleValue() != 0 && driverSet.getAcceptDistance().subtract(currentDistance).doubleValue() < 0) {
                    continue;
                }
                //订单里程判断，orderDistance==0：不限制
                if (driverSet.getOrderDistance().doubleValue() != 0 && driverSet.getOrderDistance().subtract(searchNearByDriverForm.getMileageDistance()).doubleValue() < 0) {
                    continue;
                }
                //满足条件的附近司机信息
                NearByDriverVo nearByDriverVo = new NearByDriverVo();
                nearByDriverVo.setDriverId(driverId);
                nearByDriverVo.setDistance(currentDistance);
                nearByDrivers.add(nearByDriverVo);
            }
            return nearByDrivers;
        }
        return null;
    }

    /**
     * 司机赶往代驾起始点：更新订单地址到缓存
     * @param updateOrderLocationForm
     * @return
     */
    @Override
    public Boolean updateOrderLocationToCache(UpdateOrderLocationForm updateOrderLocationForm) {
        OrderLocationVo orderLocationVo = new OrderLocationVo();
        orderLocationVo.setLongitude(updateOrderLocationForm.getLongitude());
        orderLocationVo.setLatitude(updateOrderLocationForm.getLatitude());
        redisTemplate.opsForValue().set(RedisConstant.UPDATE_ORDER_LOCATION + updateOrderLocationForm.getOrderId(), orderLocationVo);
        return true;
    }

    /**
     * 司机赶往代驾起始点：获取订单经纬度位置
     * @param orderId
     * @return
     */
    @Override
    public OrderLocationVo getCacheOrderLocation(Long orderId) {
        String orderLocationJson = (String)redisTemplate.opsForValue().get(RedisConstant.UPDATE_ORDER_LOCATION + orderId);
        OrderLocationVo orderLocationVo = JSONObject.parseObject(orderLocationJson, OrderLocationVo.class);
        return orderLocationVo;
    }

    /**
     * 开始代驾服务：保存代驾服务订单位置(MongoDB)
     * @param orderLocationServiceFormList
     * @return
     */
    @Override
    public Boolean saveOrderServiceLocation(List<OrderServiceLocationForm> orderLocationServiceFormList) {
        List<OrderServiceLocation> orderServiceLocationList = orderLocationServiceFormList.stream().map(form -> {
            OrderServiceLocation orderServiceLocation = new OrderServiceLocation();
            BeanUtils.copyProperties(form, orderServiceLocation);
            orderServiceLocation.setId(ObjectId.get().toString());
            orderServiceLocation.setCreateTime(new Date());
            return orderServiceLocation;
        }).toList();
        orderServiceLocationRepository.saveAll(orderServiceLocationList);
        return true;
    }

    /**
     * 代驾服务：获取订单服务最后一个位置信息
     * @param orderId
     * @return
     */
    @Override
    public OrderServiceLastLocationVo getOrderServiceLastLocation(Long orderId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("orderId").is(orderId));
        query.with(Sort.by(Sort.Order.desc("createTime")));
        query.limit(1);
        OrderServiceLocation orderServiceLocation = mongoTemplate.findOne(query, OrderServiceLocation.class);

        if(orderServiceLocation!=null){
            OrderServiceLastLocationVo orderServiceLastLocationVo = new OrderServiceLastLocationVo();
            BeanUtils.copyProperties(orderServiceLocation,orderServiceLastLocationVo);
            return orderServiceLastLocationVo;
        }
        return null;
    }

    /**
     * 代驾服务：计算订单实际里程
     * @param orderId
     * @return
     */
    @Override
    public BigDecimal calculateOrderRealDistance(Long orderId) {
        //1.根据订单id获取代驾订单位置信息，根据创建时间排序(升序)
        /**
         * 第一种方式查询
         *   OrderServiceLocation orderServiceLocation = new OrderServiceLocation();
         *   orderServiceLocation.setOrderId(orderId);
         *   Example<OrderServiceLocation> example = Example.of(orderServiceLocation);
         *   Sort sort = Sort.by(Sort.Direction.ASC, "createTime");
         *   List<OrderServiceLocation> locationList = orderServiceLocationRepository.findAll(example, sort);
        **/
        /**
         *  第二种方法，MongoRepository只需要按照规则在MongoRepository中把查询的方法创建出来就可以
         *  规则：
         *      1）查询方法的名称以 get、find、read开头
         *      2）在这些开头后面加上查询的字段名称，满足驼峰命名
         *      3）字段查询条件添加关键字，比如Like、OrderBy、Asc
         *  示例：
         *      findOrderIdOrderByCreateTimeAsc(orderId)
         */
        List<OrderServiceLocation> locationList
                = orderServiceLocationRepository.findOrderIdOrderByCreateTimeAsc(orderId);
        //2.把list集合遍历，得到每个位置信息，计算总里程
        double realDistance = 0;
        if(!locationList.isEmpty()){
            for (int i = 0; i < locationList.size()-1; i++) {
                OrderServiceLocation preLocation = locationList.get(i);
                OrderServiceLocation postLocation = locationList.get(i + 1);
                double distance = LocationUtil.getDistance(preLocation.getLatitude().doubleValue(), preLocation.getLongitude().doubleValue()
                        , postLocation.getLatitude().doubleValue(), postLocation.getLongitude().doubleValue());
                realDistance += distance;
            }
        }
        //3.返回总里程
        return new BigDecimal(realDistance);
    }
}
