package com.atguigu.daijia.driver.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.execption.GlobalException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.config.TencentCloudProperties;
import com.atguigu.daijia.driver.mapper.*;
import com.atguigu.daijia.driver.service.CosService;
import com.atguigu.daijia.driver.service.DriverInfoService;
import com.atguigu.daijia.model.entity.driver.*;
import com.atguigu.daijia.model.form.driver.DriverFaceModelForm;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.iai.v20200303.IaiClient;
import com.tencentcloudapi.iai.v20200303.models.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Date;

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
    @Resource
    private DriverFaceRecognitionMapper driverFaceRecognitionMapper;

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

    /**
     * 获取司机设置信息
     *
     * @param driverId
     * @return
     */
    @Override
    public DriverSet getDriverSet(Long driverId) {
        return driverSetMapper.selectOne(new LambdaQueryWrapper<DriverSet>()
                .eq(DriverSet::getDriverId, driverId));
    }

    /**
     * 判断司机当日是否进行过人脸识别
     *
     * @param driverId
     * @return
     */
    @Override
    public Boolean isFaceRecognition(Long driverId) {
        String currentTime = new DateTime().toString("yyyy-MM-dd");
        DriverFaceRecognition driverFaceRecognition = driverFaceRecognitionMapper.selectOne(new LambdaQueryWrapper<DriverFaceRecognition>()
                .eq(DriverFaceRecognition::getDriverId, driverId)
                .eq(DriverFaceRecognition::getFaceDate, currentTime));
        return driverFaceRecognition != null;
    }

    /**
     * 人脸静态活体检测
     * 文档地址：
     * https://cloud.tencent.com/document/api/867/48501
     * https://console.cloud.tencent.com/api/explorer?Product=iai&Version=2020-03-03&Action=DetectLiveFace
     * 验证司机人脸
     *
     * @param driverFaceModelForm
     * @return
     */
    @Override
    public Boolean verifyDriverFace(DriverFaceModelForm driverFaceModelForm) {
        //1.照片对比
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
            VerifyFaceRequest req = new VerifyFaceRequest();
            // 设置请求参数
            req.setImage(driverFaceModelForm.getImageBase64());
            req.setPersonId(driverFaceModelForm.getDriverId().toString());
            // 返回的resp是一个VerifyFaceResponse的实例，与请求对象对应
            VerifyFaceResponse resp = client.VerifyFace(req);
            if(resp.getIsMatch()){
                //2.如果照片比对成功，静态活体检测
                Boolean isSuccess = this.detectLiveFace(driverFaceModelForm.getImageBase64());
                if(isSuccess){
                    //3.如果静态活体检测通过，添加数据到认证表里面
                    DriverFaceRecognition driverFaceRecognition = new DriverFaceRecognition();
                    driverFaceRecognition.setDriverId(driverFaceModelForm.getDriverId());
                    driverFaceRecognition.setFaceDate(new Date());
                    driverFaceRecognitionMapper.insert(driverFaceRecognition);
                    return true;
                }
            }
        } catch (TencentCloudSDKException e) {
            System.out.println(e.toString());
        }
        throw new GlobalException(ResultCodeEnum.FACE_FAIL);
    }

    /**
     * 人脸静态活体检测
     * 文档地址：
     * https://cloud.tencent.com/document/api/867/48501
     * https://console.cloud.tencent.com/api/explorer?Product=iai&Version=2020-03-03&Action=DetectLiveFace
     * 人脸静态活体检测
     * @param imageBase64
     * @return
     */
    private Boolean detectLiveFace(String imageBase64) {
        try{
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
            DetectLiveFaceRequest req = new DetectLiveFaceRequest();
            req.setImage(imageBase64);
            // 返回的resp是一个DetectLiveFaceResponse的实例，与请求对象对应
            DetectLiveFaceResponse resp = client.DetectLiveFace(req);
            // 输出json格式的字符串回包
            System.out.println(DetectLiveFaceResponse.toJsonString(resp));
            if(resp.getIsLiveness()) {
                return true;
            }
        } catch (TencentCloudSDKException e) {
            System.out.println(e.toString());
        }
        return false;
    }

    /**
     * 更新接单状态
     * @param driverId
     * @param status
     * @return
     */
    @Override
    public Boolean updateServiceStatus(Long driverId, Integer status) {
        DriverFaceRecognition driverFaceRecognition = driverFaceRecognitionMapper.selectOne(new LambdaQueryWrapper<DriverFaceRecognition>()
                .eq(DriverFaceRecognition::getDriverId, driverId)
                .eq(DriverFaceRecognition::getFaceDate,new DateTime().toString("yyyy-MM-dd")));
        if(driverFaceRecognition==null){
            return false;
        }else{
            DriverSet driverSet = new DriverSet();
            driverSet.setServiceStatus(status);
            driverSetMapper.update(driverSet,new LambdaQueryWrapper<DriverSet>()
                    .eq(DriverSet::getDriverId,driverId));
            return true;
        }
    }

    /**
     * 获取司机基本信息
     * @param driverId
     * @return
     */
    @Override
    public DriverInfoVo getDriverInfo(Long driverId) {
        DriverInfo driverInfo = driverInfoMapper.selectOne(new LambdaQueryWrapper<DriverInfo>()
                .eq(DriverInfo::getId, driverId));
        if(driverInfo!=null){
            DriverInfoVo driverInfoVo = new DriverInfoVo();
            BeanUtils.copyProperties(driverInfo,driverInfoVo);
            //驾龄
            Integer driverLicenseAge = new DateTime().getYear() - new DateTime(driverInfo.getDriverLicenseIssueDate()).getYear() + 1;
            driverInfoVo.setDriverLicenseAge(driverLicenseAge);
            return driverInfoVo;
        }
        return null;
    }

    /**
     * 获取司机OpenId
     * @param driverId
     * @return
     */
    @Override
    public String getDriverOpenId(Long driverId) {
        DriverInfo driverInfo = this.getOne(new LambdaQueryWrapper<DriverInfo>()
                .eq(DriverInfo::getId, driverId)
                .select(DriverInfo::getWxOpenId));
        return driverInfo.getWxOpenId();
    }
}