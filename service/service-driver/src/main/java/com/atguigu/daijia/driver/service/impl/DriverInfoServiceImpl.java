package com.atguigu.daijia.driver.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.execption.GlobalException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.config.TencentCloudProperties;
import com.atguigu.daijia.driver.mapper.DriverAccountMapper;
import com.atguigu.daijia.driver.mapper.DriverInfoMapper;
import com.atguigu.daijia.driver.mapper.DriverLoginLogMapper;
import com.atguigu.daijia.driver.mapper.DriverSetMapper;
import com.atguigu.daijia.driver.service.CosService;
import com.atguigu.daijia.driver.service.DriverInfoService;
import com.atguigu.daijia.model.entity.driver.DriverAccount;
import com.atguigu.daijia.model.entity.driver.DriverInfo;
import com.atguigu.daijia.model.entity.driver.DriverLoginLog;
import com.atguigu.daijia.model.entity.driver.DriverSet;
import com.atguigu.daijia.model.form.driver.DriverFaceModelForm;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.iai.v20200303.IaiClient;
import com.tencentcloudapi.iai.v20200303.models.CreatePersonRequest;
import com.tencentcloudapi.iai.v20200303.models.CreatePersonResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.beans.BeanUtils;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Slf4j
@Service

public class DriverInfoServiceImpl extends ServiceImpl<DriverInfoMapper, DriverInfo> implements DriverInfoService {
    @Resource
    private WxMaService wxMaService;
    @Resource
    private DriverInfoMapper driverInfoMapper;
    @Resource
    private DriverSetMapper driverSetMapper;
    @Resource
    private DriverAccountMapper driverAccountMapper;
    @Resource
    private DriverLoginLogMapper driverLoginLogMapper;
    @Resource
    private CosService cosService;
    @Resource
    private TencentCloudProperties tencentCloudProperties;

    /**
     * 小程序授权登录
     *
     * @param code
     * @return
     */
    @Override
    public Long login(String code) {
        String openId = "";
        try {
            WxMaJscode2SessionResult info = wxMaService.getUserService().getSessionInfo(code);
            openId = info.getOpenid();
        } catch (WxErrorException e) {
            throw new GlobalException(ResultCodeEnum.WX_CODE_ERROR);
        }
        DriverInfo driverInfo = driverInfoMapper.selectOne(new LambdaQueryWrapper<DriverInfo>()
                .eq(DriverInfo::getWxOpenId, openId));
        if (driverInfo == null) {
            driverInfo = new DriverInfo();
            driverInfo.setNickname(String.valueOf(System.currentTimeMillis()));
            driverInfo.setAvatarUrl("https://oss.aliyuncs.com/aliyun_id_photo_bucket/default_handsome.jpg");
            driverInfo.setWxOpenId(openId);
            this.save(driverInfo);
            //初始化默认设置
            DriverSet driverSet = new DriverSet();
            driverSet.setDriverId(driverInfo.getId());
            driverSet.setOrderDistance(new BigDecimal(0));//0：无限制
            driverSet.setAcceptDistance(new BigDecimal(SystemConstant.ACCEPT_DISTANCE));//默认接单范围：5公里
            driverSet.setIsAutoAccept(0);//0：否 1：是
            driverSetMapper.insert(driverSet);
            //初始化司机账户
            DriverAccount driverAccount = new DriverAccount();
            driverAccount.setDriverId(driverInfo.getId());
            driverAccountMapper.insert(driverAccount);
        }
        //登录日志
        DriverLoginLog driverLoginLog = new DriverLoginLog();
        driverLoginLog.setDriverId(driverInfo.getId());
        driverLoginLog.setMsg("小程序登录");
        driverLoginLogMapper.insert(driverLoginLog);
        return driverInfo.getId();
    }

    /**
     * 获取司机登录信息
     *
     * @param driverId
     * @return
     */
    @Override
    public DriverLoginVo getDriverLoginInfo(Long driverId) {
        DriverInfo driverInfo = driverInfoMapper.selectOne(new LambdaQueryWrapper<DriverInfo>()
                .eq(DriverInfo::getId, driverId));
        DriverLoginVo driverLoginVo = new DriverLoginVo();
        BeanUtils.copyProperties(driverInfo, driverLoginVo);
        //是否创建人脸库人员，接单时做人脸识别判断
        Boolean isArchiveFace = StringUtils.hasText(driverInfo.getFaceModelId());
        driverLoginVo.setIsArchiveFace(isArchiveFace);
        return driverLoginVo;
    }

    /**
     * 获取司机认证信息
     *
     * @param driverId
     * @return
     */
    @Override
    public DriverAuthInfoVo getDriverAuthInfo(Long driverId) {
        DriverInfo driverInfo = driverInfoMapper.selectOne(new LambdaQueryWrapper<DriverInfo>()
                .eq(DriverInfo::getId, driverId));
        DriverAuthInfoVo driverAuthInfoVo = new DriverAuthInfoVo();
        BeanUtils.copyProperties(driverInfo, driverAuthInfoVo);
        driverAuthInfoVo.setIdcardBackShowUrl(cosService.getImageUrl(driverAuthInfoVo.getIdcardBackUrl()));
        driverAuthInfoVo.setIdcardFrontShowUrl(cosService.getImageUrl(driverAuthInfoVo.getIdcardFrontUrl()));
        driverAuthInfoVo.setIdcardHandShowUrl(cosService.getImageUrl(driverAuthInfoVo.getIdcardHandUrl()));
        driverAuthInfoVo.setDriverLicenseFrontShowUrl(cosService.getImageUrl(driverAuthInfoVo.getDriverLicenseFrontUrl()));
        driverAuthInfoVo.setDriverLicenseBackShowUrl(cosService.getImageUrl(driverAuthInfoVo.getDriverLicenseBackUrl()));
        driverAuthInfoVo.setDriverLicenseHandShowUrl(cosService.getImageUrl(driverAuthInfoVo.getDriverLicenseHandUrl()));
        return driverAuthInfoVo;
    }

    /**
     * 更新司机认证信息
     *
     * @param updateDriverAuthInfoForm
     * @return
     */
    @Override
    public Boolean updateDriverAuthInfo(UpdateDriverAuthInfoForm updateDriverAuthInfoForm) {
        DriverInfo driverInfo = new DriverInfo();
        BeanUtils.copyProperties(updateDriverAuthInfoForm, driverInfo);
        driverInfo.setId(updateDriverAuthInfoForm.getDriverId());
        int result = driverInfoMapper.updateById(driverInfo);
        return result > 0;
    }

    /**
     * 创建司机人脸模型
     *
     * @param driverFaceModelForm
     * @return
     */
    @Override
    public Boolean creatDriverFaceModel(DriverFaceModelForm driverFaceModelForm) {
        DriverInfo driverInfo = driverInfoMapper.selectOne(new LambdaQueryWrapper<DriverInfo>()
                .eq(DriverInfo::getId, driverFaceModelForm.getDriverId()));
        try {
            // 实例化一个认证对象，入参需要传入腾讯云账户 SecretId 和 SecretKey，此处还需注意密钥对的保密
            // 代码泄露可能会导致 SecretId 和 SecretKey 泄露，并威胁账号下所有资源的安全性。以下代码示例仅供参考，建议采用更安全的方式来使用密钥，请参见：https://cloud.tencent.com/document/product/1278/85305
            // 密钥可前往官网控制台 https://console.cloud.tencent.com/cam/capi 进行获取
            Credential cred = new Credential(tencentCloudProperties.getSecretId(), tencentCloudProperties.getSecretKey());
            // 实例化一个http选项，可选的，没有特殊需求可以跳过
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("iai.tencentcloudapi.com");
            // 实例化一个client选项，可选的，没有特殊需求可以跳过
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            // 实例化要请求产品的client对象,clientProfile是可选的
            IaiClient client = new IaiClient(cred, tencentCloudProperties.getRegion(), clientProfile);
            // 实例化一个请求对象,每个接口都会对应一个request对象
            CreatePersonRequest req = new CreatePersonRequest();
            req.setGroupId(tencentCloudProperties.getPersonGroupId());
            req.setUniquePersonControl(4L);
            req.setQualityControl(4L);
            req.setImage(driverFaceModelForm.getImageBase64());
            req.setPersonId(String.valueOf(driverInfo.getId()));
            req.setGender(Long.parseLong(driverInfo.getGender()));
            req.setPersonName(driverInfo.getName());
            // 返回的resp是一个CreatePersonResponse的实例，与请求对象对应
            CreatePersonResponse resp = client.CreatePerson(req);
            // 更新司机信息
            if (StringUtils.hasText(resp.getFaceId())) {
                driverInfo.setFaceModelId(resp.getFaceId());
                driverInfoMapper.updateById(driverInfo);
            }
        } catch (TencentCloudSDKException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}