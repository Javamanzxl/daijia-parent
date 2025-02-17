package com.atguigu.daijia.driver.controller;

import com.atguigu.daijia.common.execption.GlobalException;
import com.atguigu.daijia.common.login.UserLogin;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.util.AuthContextHolder;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.driver.service.LocationService;
import com.atguigu.daijia.model.entity.driver.DriverSet;
import com.atguigu.daijia.model.form.map.OrderServiceLocationForm;
import com.atguigu.daijia.model.form.map.UpdateDriverLocationForm;
import com.atguigu.daijia.model.form.map.UpdateOrderLocationForm;
import com.atguigu.daijia.model.vo.map.OrderServiceLastLocationVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "位置API接口管理")
@RestController
@RequestMapping(value="/location")
public class LocationController {
    @Resource
    private LocationService locationService;
    @Resource
    private DriverInfoFeignClient driverInfoFeignClient;

    @Operation(summary = "开启接单服务：更新司机经纬度位置")
    @UserLogin
    @PostMapping("/updateDriverLocation")
    public Result<Boolean> updateDriverLocation(@RequestBody UpdateDriverLocationForm updateDriverLocationForm) {
        Long driverId = AuthContextHolder.getUserId();
        updateDriverLocationForm.setDriverId(driverId);
        Result<DriverSet> result = driverInfoFeignClient.getDriverSet(driverId);
        if(result.getCode()!=200){
            throw new GlobalException(ResultCodeEnum.FEIGN_FAIL);
        }
        DriverSet driverSet = result.getData();
        if(driverSet.getServiceStatus()==1){
            return Result.ok(locationService.updateDriverLocation(updateDriverLocationForm));
        }else{
            throw new GlobalException(ResultCodeEnum.NO_START_SERVICE);
        }

    }

    @Operation(summary = "关闭接单服务：删除司机经纬度位置")
    @UserLogin
    @DeleteMapping("/removeDriverLocation")
    public Result<Boolean> removeDriverLocation(){
        Long driverId = AuthContextHolder.getUserId();
        return Result.ok(locationService.removeDriverLocation(driverId));
    }

    @Operation(summary = "司机赶往代驾起始点：更新订单位置到Redis缓存")
    @UserLogin
    @PostMapping("/updateOrderLocationToCache")
    public Result<Boolean> updateOrderLocationToCache(@RequestBody UpdateOrderLocationForm updateOrderLocationForm) {
        return Result.ok(locationService.updateOrderLocationToCache(updateOrderLocationForm));
    }
    @Operation(summary = "开始代驾服务：保存代驾服务订单位置")
    @PostMapping("/saveOrderServiceLocation")
    public Result<Boolean> saveOrderServiceLocation(@RequestBody List<OrderServiceLocationForm> orderLocationServiceFormList) {
        return Result.ok(locationService.saveOrderServiceLocation(orderLocationServiceFormList));
    }

}

