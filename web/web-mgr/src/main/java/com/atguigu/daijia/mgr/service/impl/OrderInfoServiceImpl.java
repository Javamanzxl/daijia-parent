package com.atguigu.daijia.mgr.service.impl;

import com.atguigu.daijia.mgr.service.OrderInfoService;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service

public class OrderInfoServiceImpl implements OrderInfoService {

	@Resource
	private OrderInfoFeignClient orderInfoFeignClient;



}
