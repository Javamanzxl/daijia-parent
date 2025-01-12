package com.atguigu.daijia.customer.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.binarywang.wx.miniapp.bean.WxMaPhoneNumberInfo;
import com.atguigu.daijia.common.execption.GlobalException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.customer.mapper.CustomerInfoMapper;
import com.atguigu.daijia.customer.mapper.CustomerLoginLogMapper;
import com.atguigu.daijia.customer.service.CustomerInfoService;
import com.atguigu.daijia.model.entity.customer.CustomerInfo;
import com.atguigu.daijia.model.entity.customer.CustomerLoginLog;
import com.atguigu.daijia.model.form.customer.UpdateWxPhoneForm;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service

public class CustomerInfoServiceImpl extends ServiceImpl<CustomerInfoMapper, CustomerInfo> implements CustomerInfoService {

    @Resource
    private CustomerInfoMapper customerInfoMapper;
    @Resource
    private CustomerLoginLogMapper customerLoginLogMapper;
    @Resource
    private WxMaService wxMaService;
    /**
     * 微信小程序登陆
     * @param code
     * @return
     */
    @Override
    public Long login(String code) {
        //1.获取code值，使用微信工具包对象，获取微信唯一标识openid
        String openid="";
        try {
            WxMaJscode2SessionResult sessionInfo = wxMaService.getUserService().getSessionInfo(code);
            openid = sessionInfo.getOpenid();
        } catch (WxErrorException e) {
            log.error("获取微信openid失败：{}",e.getMessage());
            throw new GlobalException(ResultCodeEnum.WX_CODE_ERROR);
        }
        //2.根据openid查询数据库，判断是否第一次登陆
        CustomerInfo customerInfo = customerInfoMapper.selectOne(new LambdaQueryWrapper<CustomerInfo>().eq(CustomerInfo::getWxOpenId, openid));
        //3.如果第一次登陆，添加数据库
        if(customerInfo==null){
            customerInfo = new CustomerInfo();
            customerInfo.setNickname(String.valueOf(System.currentTimeMillis()));
            customerInfo.setAvatarUrl("https://oss.aliyuncs.com/aliyun_id_photo_bucket/default_handsome.jpg");
            customerInfo.setWxOpenId(openid);
            customerInfoMapper.insert(customerInfo);
        }
        //4.记录登陆日志
        CustomerLoginLog loginLog = new CustomerLoginLog();
        loginLog.setCustomerId(customerInfo.getId());
        loginLog.setMsg("小程序登录");
        customerLoginLogMapper.insert(loginLog);
        //5.返回用户id
        return customerInfo.getId();
    }

    /**
     * 获取客户登录信息
     * @param customerId
     * @return
     */
    @Override
    public CustomerLoginVo getCustomerLoginInfo(Long customerId) {
        CustomerInfo customerInfo = customerInfoMapper.selectById(customerId);
        CustomerLoginVo customerLoginVo = new CustomerLoginVo();
        BeanUtils.copyProperties(customerInfo,customerLoginVo);
        boolean isBindPhone = StringUtils.hasText(customerInfo.getPhone());
        customerLoginVo.setIsBindPhone(isBindPhone);
        return customerLoginVo;
    }

    /**
     * 更新客户微信手机号码
     * @param updateWxPhoneForm
     * @return
     */
    @Override
    public Boolean updateWxPhoneNumber(UpdateWxPhoneForm updateWxPhoneForm) {
        try {
            WxMaPhoneNumberInfo phoneNoInfo = wxMaService.getUserService().getPhoneNoInfo(updateWxPhoneForm.getCode());
            String phoneNumber = phoneNoInfo.getPhoneNumber();
            CustomerInfo customerInfo = new CustomerInfo();
            customerInfo.setId(updateWxPhoneForm.getCustomerId());
            customerInfo.setPhone(phoneNumber);
            return this.updateById(customerInfo);
        } catch (WxErrorException e) {
            throw new RuntimeException(e);
        }
    }
}
