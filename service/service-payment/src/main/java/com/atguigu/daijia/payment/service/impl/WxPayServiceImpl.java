package com.atguigu.daijia.payment.service.impl;

import com.alibaba.fastjson2.JSON;
import com.atguigu.daijia.common.constant.MqConst;
import com.atguigu.daijia.common.execption.GlobalException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.service.RabbitService;
import com.atguigu.daijia.common.util.RequestUtils;
import com.atguigu.daijia.driver.client.DriverAccountFeignClient;
import com.atguigu.daijia.model.entity.payment.PaymentInfo;
import com.atguigu.daijia.model.enums.TradeType;
import com.atguigu.daijia.model.form.driver.TransferForm;
import com.atguigu.daijia.model.form.payment.PaymentInfoForm;
import com.atguigu.daijia.model.vo.order.OrderRewardVo;
import com.atguigu.daijia.model.vo.payment.WxPrepayVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import com.atguigu.daijia.payment.config.WxPayV3Properties;
import com.atguigu.daijia.payment.mapper.PaymentInfoMapper;
import com.atguigu.daijia.payment.service.WxPayService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.model.*;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.model.Transaction;
import io.seata.spring.annotation.GlobalTransactional;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.RequestUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.math.BigDecimal;
import java.util.Date;

@Service
@Slf4j
public class WxPayServiceImpl implements WxPayService {

    @Resource
    private PaymentInfoMapper paymentInfoMapper;
    @Resource
    private RSAAutoCertificateConfig rsaAutoCertificateConfig;
    @Resource
    private WxPayV3Properties wxPayV3Properties;
    @Resource
    private RabbitService rabbitService;
    @Resource
    private OrderInfoFeignClient orderInfoFeignClient;
    @Resource
    private DriverAccountFeignClient driverAccountFeignClient;


    /**
     * 创建微信支付
     *
     * @param paymentInfoForm
     * @return
     */
    @Override
    public WxPrepayVo createWxPayment(PaymentInfoForm paymentInfoForm) {
        try {
            //1.添加支付记录到支付表里,判断：如果表存在订单支付记录，不需要添加
            PaymentInfo paymentInfo = paymentInfoMapper.selectOne(new LambdaQueryWrapper<PaymentInfo>()
                    .eq(PaymentInfo::getOrderNo, paymentInfoForm.getOrderNo()));
            if (paymentInfo == null) {
                paymentInfo = new PaymentInfo();
                BeanUtils.copyProperties(paymentInfoForm, paymentInfo);
                paymentInfo.setPaymentStatus(0);
                paymentInfoMapper.insert(paymentInfo);
            }
            //2.创建微信支付使用对象
            JsapiServiceExtension service = new JsapiServiceExtension.Builder().config(rsaAutoCertificateConfig).build();
            //3.创建request对象，封装微信支付需要的参数
            // request.setXxx(val)设置所需参数，具体参数可见Request定义
            PrepayRequest request = new PrepayRequest();
            Amount amount = new Amount();
            amount.setTotal(paymentInfoForm.getAmount().multiply(new BigDecimal(100)).intValue());
            request.setAmount(amount);
            request.setAppid(wxPayV3Properties.getAppid());
            request.setMchid(wxPayV3Properties.getMerchantId());
            //string[1,127]
            String description = paymentInfo.getContent();
            if (description.length() > 127) {
                description = description.substring(0, 127);
            }
            request.setDescription(description);
            request.setNotifyUrl(wxPayV3Properties.getNotifyUrl());
            request.setOutTradeNo(paymentInfo.getOrderNo());
            //获取用户信息
            Payer payer = new Payer();
            payer.setOpenid(paymentInfoForm.getCustomerOpenId());
            request.setPayer(payer);
            //是否指定分账，不指定不能分账
            SettleInfo settleInfo = new SettleInfo();
            settleInfo.setProfitSharing(true);
            request.setSettleInfo(settleInfo);
            //4.调用微信支付使用对象里面方法实现微信支付调用
            PrepayWithRequestPaymentResponse response = service.prepayWithRequestPayment(request);
            //5.根据返回结果，封装对象
            WxPrepayVo wxPrepayVo = new WxPrepayVo();
            BeanUtils.copyProperties(response, wxPrepayVo);
            wxPrepayVo.setTimeStamp(response.getTimeStamp());
            return wxPrepayVo;
        } catch (Exception e) {
            throw new GlobalException(ResultCodeEnum.DATA_ERROR);
        }
    }

    /**
     * 支付状态查询
     *
     * @param orderNo
     * @return
     */
    @Override
    public Boolean queryPayStatus(String orderNo) {
        //1.创建微信操作对象
        JsapiServiceExtension service = new JsapiServiceExtension.Builder().config(rsaAutoCertificateConfig).build();
        //2.封装支付状态需要的参数
        QueryOrderByOutTradeNoRequest queryRequest = new QueryOrderByOutTradeNoRequest();
        queryRequest.setOutTradeNo(orderNo);
        queryRequest.setMchid(wxPayV3Properties.getMerchantId());
        //3.调用微信操作对象的方法
        Transaction transaction = service.queryOrderByOutTradeNo(queryRequest);
        //4.查询返回结果
        if (transaction != null && transaction.getTradeState() == Transaction.TradeStateEnum.SUCCESS) {
            //5.如果支付成功，调用方法实现后续业务处理
            this.handlePayment(transaction);
            return true;
        }
        return false;
    }

    /**
     * 微信支付异步通知接口
     *
     * @param request
     */
    @Override
    public void wxnotify(HttpServletRequest request) {
        //1.回调通知的验签与解密
        //从request头信息获取参数
        //HTTP 头 Wechatpay-Signature
        // HTTP 头 Wechatpay-Nonce
        //HTTP 头 Wechatpay-Timestamp
        //HTTP 头 Wechatpay-Serial
        //HTTP 头 Wechatpay-Signature-Type
        //HTTP 请求体 body。切记使用原始报文，不要用 JSON 对象序列化后的字符串，避免验签的 body 和原文不一致。
        String wechatPaySerial = request.getHeader("Wechatpay-Serial");
        String nonce = request.getHeader("Wechatpay-Nonce");
        String timestamp = request.getHeader("Wechatpay-Timestamp");
        String signature = request.getHeader("Wechatpay-Signature");
        String requestBody = RequestUtils.readData(request);
        //2.构造 RequestParam
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(wechatPaySerial)
                .nonce(nonce)
                .signature(signature)
                .timestamp(timestamp)
                .body(requestBody)
                .build();
        //3.初始化 NotificationParser
        NotificationParser parser = new NotificationParser(rsaAutoCertificateConfig);
        //4.以支付通知回调为例，验签、解密并转换成 Transaction
        Transaction transaction = parser.parse(requestParam, Transaction.class);
        if (null != transaction && transaction.getTradeState() == Transaction.TradeStateEnum.SUCCESS) {
            //5.处理支付业务
            this.handlePayment(transaction);
        }
    }


    /**
     * 支付成功，调用方法实现后续业务处理
     *
     * @param transaction
     */
    private void handlePayment(Transaction transaction) {
        //1.更新支付记录，状态修改为已经支付
        String orderNo = transaction.getOutTradeNo();
        PaymentInfo paymentInfo = paymentInfoMapper.selectOne(new LambdaQueryWrapper<PaymentInfo>()
                .eq(PaymentInfo::getOrderNo, orderNo));
        if(paymentInfo!=null){
            //如果已经支付，不需要更新
            if(paymentInfo.getPaymentStatus() == 1) {
                return;
            }
            paymentInfo.setPaymentStatus(1);
            paymentInfo.setOrderNo(transaction.getOutTradeNo());
            paymentInfo.setTransactionId(transaction.getTransactionId());
            paymentInfo.setCallbackTime(new Date());
            paymentInfo.setCallbackContent(JSON.toJSONString(transaction));
            paymentInfoMapper.updateById(paymentInfo);
        }
        //2 发送端：发送mq消息，传递 订单编号
        //  接收端：获取订单编号，完成后续处理
        rabbitService.sendMessage(MqConst.EXCHANGE_ORDER,
                MqConst.ROUTING_PAY_SUCCESS,
                orderNo);
    }

    /**
     * 支付成功后续处理
     *      * 1、更改订单状态
     *      * 2、如果有系统奖励，需要将系统奖励打入司机个人账户
     *      * 3、执行分账
     * @param orderNo
     */
    @GlobalTransactional
    @Override
    public void handleOrder(String orderNo) {
        //1、更改订单状态
        orderInfoFeignClient.updateOrderPayStatus(orderNo);
        //2、如果有系统奖励，需要将系统奖励打入司机个人账户
        OrderRewardVo orderRewardVo = orderInfoFeignClient.getOrderRewardFee(orderNo).getData();
        if(orderRewardVo!=null && orderRewardVo.getRewardFee().doubleValue()>0){
            TransferForm transferForm = new TransferForm();
            transferForm.setTradeNo(orderNo);
            transferForm.setTradeType(TradeType.REWARD.getType());
            transferForm.setContent(TradeType.REWARD.getContent());
            transferForm.setAmount(orderRewardVo.getRewardFee());
            transferForm.setDriverId(orderRewardVo.getDriverId());
            driverAccountFeignClient.transfer(transferForm);
        }
        //TODO 3、执行分账
    }
}
