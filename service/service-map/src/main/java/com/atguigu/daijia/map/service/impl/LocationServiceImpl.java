package com.atguigu.daijia.map.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.map.service.LocationService;
import com.atguigu.daijia.model.entity.driver.DriverSet;
import com.atguigu.daijia.model.form.map.SearchNearByDriverForm;
import com.atguigu.daijia.model.form.map.UpdateDriverLocationForm;
import com.atguigu.daijia.model.vo.map.NearByDriverVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class LocationServiceImpl implements LocationService {
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @Resource
    private DriverInfoFeignClient driverInfoFeignClient;

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
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = redisTemplate.opsForGeo().radius(RedisConstant.DRIVER_GEO_LOCATION, circle, args);
        //2.查询redis最终返回list
        if(geoResults!=null){
            List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = geoResults.getContent();
            List<NearByDriverVo> nearByDrivers = new ArrayList<>();
            //3.对list集合进行处理
            //遍历list集合，得到每个司机信息,根据每个司机的个性化信息判断
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> item : content) {
                Long driverId = Long.parseLong(item.getContent().getName());
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
}
