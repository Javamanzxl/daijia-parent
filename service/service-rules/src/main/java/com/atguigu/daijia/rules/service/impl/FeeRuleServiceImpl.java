package com.atguigu.daijia.rules.service.impl;

import com.atguigu.daijia.model.form.rules.FeeRuleRequest;
import com.atguigu.daijia.model.form.rules.FeeRuleRequestForm;
import com.atguigu.daijia.model.vo.rules.FeeRuleResponse;
import com.atguigu.daijia.model.vo.rules.FeeRuleResponseVo;
import com.atguigu.daijia.rules.mapper.FeeRuleMapper;
import com.atguigu.daijia.rules.service.FeeRuleService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FeeRuleServiceImpl implements FeeRuleService {
    @Resource
    private KieContainer kieContainer;
    @Override
    public FeeRuleResponseVo calculateOrderFee(FeeRuleRequestForm calculateOrderFeeForm) {
        //封装输入规则的对象
        FeeRuleRequest feeRuleRequest = new FeeRuleRequest();
        feeRuleRequest.setDistance(calculateOrderFeeForm.getDistance());
        feeRuleRequest.setWaitMinute(calculateOrderFeeForm.getWaitMinute());
        feeRuleRequest.setStartTime(new DateTime(calculateOrderFeeForm.getStartTime()).toString("HH:mm:ss"));
        //开启会话
        KieSession kieSession = kieContainer.newKieSession();
        //封装返回对象
        FeeRuleResponse feeRuleResponse = new FeeRuleResponse();
        kieSession.setGlobal("feeRuleResponse",feeRuleResponse);
        // 设置订单对象
        kieSession.insert(feeRuleRequest);
        // 触发规则
        kieSession.fireAllRules();
        // 中止会话
        kieSession.dispose();
        FeeRuleResponseVo feeRuleResponseVo = new FeeRuleResponseVo();
        BeanUtils.copyProperties(feeRuleResponse,feeRuleResponseVo);
        return feeRuleResponseVo;
    }
}
