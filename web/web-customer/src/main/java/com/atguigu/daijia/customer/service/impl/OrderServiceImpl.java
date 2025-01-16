package com.atguigu.daijia.customer.service.impl;

import com.atguigu.daijia.common.execption.GlobalException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.customer.service.OrderService;
import com.atguigu.daijia.map.client.MapFeignClient;
import com.atguigu.daijia.model.form.customer.ExpectOrderForm;
import com.atguigu.daijia.model.form.customer.SubmitOrderForm;
import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import com.atguigu.daijia.model.form.rules.FeeRuleRequestForm;
import com.atguigu.daijia.model.vo.customer.ExpectOrderVo;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import com.atguigu.daijia.model.vo.rules.FeeRuleResponseVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import com.atguigu.daijia.rules.client.FeeRuleFeignClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {
    @Resource
    private MapFeignClient mapFeignClient;
    @Resource
    private FeeRuleFeignClient feeRuleFeignClient;
    @Resource
    private OrderInfoFeignClient orderInfoFeignClient;
    /**
     * 预估订单数据
     * @param expectOrderForm
     * @return
     */
    @Override
    public ExpectOrderVo expectOrder(ExpectOrderForm expectOrderForm) {
        //计算驾驶线路
        CalculateDrivingLineForm calculateDrivingLineForm = new CalculateDrivingLineForm();
        BeanUtils.copyProperties(expectOrderForm,calculateDrivingLineForm);
        Result<DrivingLineVo> result = mapFeignClient.calculateDrivingLine(calculateDrivingLineForm);
        if(result.getCode()!=200){
            throw new GlobalException(ResultCodeEnum.FEIGN_FAIL);
        }
        DrivingLineVo drivingLineVo = result.getData();
        //计算订单费用
        FeeRuleRequestForm feeRuleRequestForm = new FeeRuleRequestForm();
        feeRuleRequestForm.setDistance(drivingLineVo.getDistance());
        feeRuleRequestForm.setStartTime(new Date());
        feeRuleRequestForm.setWaitMinute(0);
        Result<FeeRuleResponseVo> ruleResult = feeRuleFeignClient.calculateOrderFee(feeRuleRequestForm);
        if(ruleResult.getCode()!=200){
            throw new GlobalException(ResultCodeEnum.FEIGN_FAIL);
        }
        FeeRuleResponseVo fee = ruleResult.getData();
        //预估订单实体
        ExpectOrderVo expectOrderVo = new ExpectOrderVo();
        expectOrderVo.setDrivingLineVo(drivingLineVo);
        expectOrderVo.setFeeRuleResponseVo(fee);
        return expectOrderVo;
    }

    /**
     * 乘客下单
     * @param submitOrderForm
     * @return
     */
    @Override
    public Long submitOrder(SubmitOrderForm submitOrderForm) {
        //1.重新计算驾驶线路
        CalculateDrivingLineForm calculateDrivingLineForm = new CalculateDrivingLineForm();
        BeanUtils.copyProperties(submitOrderForm, calculateDrivingLineForm);
        DrivingLineVo drivingLineVo = mapFeignClient.calculateDrivingLine(calculateDrivingLineForm).getData();

        //2.重新计算订单费用
        FeeRuleRequestForm calculateOrderFeeForm = new FeeRuleRequestForm();
        calculateOrderFeeForm.setDistance(drivingLineVo.getDistance());
        calculateOrderFeeForm.setStartTime(new Date());
        calculateOrderFeeForm.setWaitMinute(0);
        FeeRuleResponseVo feeRuleResponseVo = feeRuleFeignClient.calculateOrderFee(calculateOrderFeeForm).getData();
        //3.封装订单信息对象
        OrderInfoForm orderInfoForm = new OrderInfoForm();
        //订单位置信息
        BeanUtils.copyProperties(submitOrderForm,orderInfoForm);
        //预估里程
        orderInfoForm.setExpectDistance(drivingLineVo.getDistance());
        orderInfoForm.setExpectAmount(feeRuleResponseVo.getTotalAmount());
        Result<Long> result = orderInfoFeignClient.saveOrderInfo(orderInfoForm);
        if(result.getCode()!=200){
            throw new GlobalException(ResultCodeEnum.FEIGN_FAIL);
        }
        Long orderId = result.getData();
        //TODO 启动任务调度
        return orderId;
    }

    /**
     * 查询订单状态
     * @param orderId
     * @return
     */
    @Override
    public Integer getOrderStatus(Long orderId) {
        Result<Integer> result = orderInfoFeignClient.getOrderStatus(orderId);
        if(result.getCode()!=200){
            throw new GlobalException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }
}
