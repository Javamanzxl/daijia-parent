package com.atguigu.daijia.customer.controller;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.login.UserLogin;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.util.AuthContextHolder;
import com.atguigu.daijia.customer.client.CustomerInfoFeignClient;
import com.atguigu.daijia.customer.service.CustomerService;
import com.atguigu.daijia.model.entity.customer.CustomerInfo;
import com.atguigu.daijia.model.form.customer.UpdateWxPhoneForm;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "客户API接口管理")
@RestController
@RequestMapping("/customer")
public class CustomerController {
    @Resource
    private CustomerService customerService;
    @Resource
    private CustomerInfoFeignClient customerInfoFeignClient;
    @Resource
    private RedisTemplate<String, String> redisTemplate;


    @Operation(summary = "小程序授权登录")
    @GetMapping("/login/{code}")
    public Result<String> wxLogin(@PathVariable String code) {
        String token = customerService.login(code);
        return Result.ok(token);
    }


    @Operation(summary = "获取登陆信息")
    @GetMapping("/getCustomerLoginInfo")
    @UserLogin
    public Result<CustomerLoginVo> getCustomerLoginInfo(HttpServletRequest request
                                                        /*@RequestHeader(value = "token") String token*/) {
        Long customerId = AuthContextHolder.getUserId();
        CustomerLoginVo customer = customerService.getCustomerLoginInfo(customerId);
        return Result.ok(customer);
    }

    @Operation(summary = "更新用户微信手机号")
    @UserLogin
    @PostMapping("/updateWxPhone")
    public Result updateWxPhone(@RequestBody UpdateWxPhoneForm updateWxPhoneForm) {
        updateWxPhoneForm.setCustomerId(AuthContextHolder.getUserId());
        //企业级微信才可以调用手机号因此直接跳过customerInfoFeignClient.updateWxPhoneNumber(updateWxPhoneForm);
        return Result.ok(true);
    }

}

