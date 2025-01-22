package com.atguigu.daijia.rules.service.impl;

import com.atguigu.daijia.model.form.rules.RewardRuleRequest;
import com.atguigu.daijia.model.form.rules.RewardRuleRequestForm;
import com.atguigu.daijia.model.vo.rules.RewardRuleResponse;
import com.atguigu.daijia.model.vo.rules.RewardRuleResponseVo;
import com.atguigu.daijia.rules.mapper.RewardRuleMapper;
import com.atguigu.daijia.rules.service.RewardRuleService;
import com.atguigu.daijia.rules.utils.DroolsHelper;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RewardRuleServiceImpl implements RewardRuleService {

    /**
     * 计算订单奖励费用
     * @param rewardRuleRequestForm
     * @return
     */
    @Override
    public RewardRuleResponseVo calculateOrderRewardFee(RewardRuleRequestForm rewardRuleRequestForm) {
        //封装传入的参数
        RewardRuleRequest rewardRuleRequest = new RewardRuleRequest();
        BeanUtils.copyProperties(rewardRuleRequestForm,rewardRuleRequest);
        //封装返回对象
        RewardRuleResponse rewardRuleResponse = new RewardRuleResponse();
        //创建规则引擎对象
        KieSession kieSession = DroolsHelper.loadForRule("rules/RewardRule.drl");
        kieSession.insert(rewardRuleRequest);
        kieSession.setGlobal("rewardRuleResponse",rewardRuleResponse);
        kieSession.fireAllRules();
        kieSession.dispose();
        //封装返回对象
        RewardRuleResponseVo rewardRuleResponseVo = new RewardRuleResponseVo();
        rewardRuleResponseVo.setRewardAmount(rewardRuleResponse.getRewardAmount());
        return rewardRuleResponseVo;
    }
}
