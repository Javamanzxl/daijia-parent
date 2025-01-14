package com.atguigu.daijia.driver.controller;

import com.atguigu.daijia.common.login.UserLogin;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.driver.service.OrderService;
import com.atguigu.daijia.model.vo.order.CurrentOrderInfoVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "订单API接口管理")
@RestController
@RequestMapping("/order")
public class OrderController {


}

