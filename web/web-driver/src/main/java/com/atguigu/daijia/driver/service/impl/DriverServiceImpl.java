package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GlobalException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.driver.service.DriverService;
import com.atguigu.daijia.model.form.driver.DriverFaceModelForm;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DriverServiceImpl implements DriverService {

    @Resource
    private DriverInfoFeignClient driverInfoFeignClient;

    @Resource
    private RedisTemplate<String,String> redisTemplate;
    /**
     * 小程序授权登录
     * @param code
     * @return
     */
    @Override
    public String login(String code) {
        //获取openId
        Long driverId = driverInfoFeignClient.login(code).getData();
        String token = UUID.randomUUID().toString().replaceAll("-", "");
        redisTemplate.opsForValue().set(RedisConstant.USER_LOGIN_KEY_PREFIX + token, driverId.toString(), RedisConstant.USER_LOGIN_KEY_TIMEOUT, TimeUnit.SECONDS);
        return token;
    }

    /**
     * 获取司机登录信息
     * @param driverId
     * @return
     */
    @Override
    public DriverLoginVo getDriverLoginInfo(Long driverId) {
        return driverInfoFeignClient.getDriverLoginInfo(driverId).getData();
    }

    @Override
    public DriverAuthInfoVo getDriverAuthInfo(Long driverId) {
        Result<DriverAuthInfoVo> result = driverInfoFeignClient.getDriverAuthInfo(driverId);
        if(result.getCode()!=200){
            throw new GlobalException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

    /**
     * 更新司机认证信息
     * @param updateDriverAuthInfoForm
     * @return
     */
    @Override
    public Boolean updateDriverAuthInfo(UpdateDriverAuthInfoForm updateDriverAuthInfoForm) {
        Result<Boolean> result = driverInfoFeignClient.UpdateDriverAuthInfo(updateDriverAuthInfoForm);
        if(result.getCode()!=200){
            throw new GlobalException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

    /**
     * 创建司机人脸模型
     * @param driverFaceModelForm
     * @return
     */
    @Override
    public Boolean creatDriverFaceModel(DriverFaceModelForm driverFaceModelForm) {
        Result<Boolean> result = driverInfoFeignClient.creatDriverFaceModel(driverFaceModelForm);
        if(result.getCode()!=200){
            throw new GlobalException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }
}
