package com.atguigu.daijia.driver.service.impl;

import com.alibaba.nacos.common.codec.Base64;
import com.atguigu.daijia.common.execption.GlobalException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.config.TencentCloudProperties;
import com.atguigu.daijia.driver.service.CosService;
import com.atguigu.daijia.driver.service.OcrService;
import com.atguigu.daijia.model.vo.driver.CosUploadVo;
import com.atguigu.daijia.model.vo.driver.DriverLicenseOcrVo;
import com.atguigu.daijia.model.vo.driver.IdCardOcrVo;
import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.ocr.v20181119.OcrClient;
import com.tencentcloudapi.ocr.v20181119.models.DriverLicenseOCRRequest;
import com.tencentcloudapi.ocr.v20181119.models.DriverLicenseOCRResponse;
import com.tencentcloudapi.ocr.v20181119.models.IDCardOCRRequest;
import com.tencentcloudapi.ocr.v20181119.models.IDCardOCRResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.format.DateTimeFormat;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class OcrServiceImpl implements OcrService {
    @Resource
    private TencentCloudProperties tencentCloudProperties;
    @Resource
    private CosService cosService;

    /**
     * 身份证识别
     *
     * @param file
     * @return
     */
    @Override
    public IdCardOcrVo idCardOcr(MultipartFile file) {
        try {
            // 图片转换到base64格式字符串、
            byte[] base64 = Base64.encodeBase64(file.getBytes());
            String file64 = new String(base64);
            // 实例化一个认证对象，入参需要传入腾讯云账户 SecretId 和 SecretKey，此处还需注意密钥对的保密
            // 代码泄露可能会导致 SecretId 和 SecretKey 泄露，并威胁账号下所有资源的安全性。以下代码示例仅供参考，建议采用更安全的方式来使用密钥，请参见：https://cloud.tencent.com/document/product/1278/85305
            // 密钥可前往官网控制台 https://console.cloud.tencent.com/cam/capi 进行获取
            OcrClient client = getOcrClient();
            // 实例化一个请求对象,每个接口都会对应一个request对象
            IDCardOCRRequest req = new IDCardOCRRequest();
            req.setImageBase64(file64);
            // 返回的resp是一个IDCardOCRResponse的实例，与请求对象对应
            IDCardOCRResponse resp = client.IDCardOCR(req);
            IdCardOcrVo idCardOcrVo = new IdCardOcrVo();
            if (StringUtils.hasText(resp.getName())) {
                //身份证正面
                idCardOcrVo.setName(resp.getName());
                idCardOcrVo.setGender("男".equals(resp.getSex()) ? "1" : "2");
                idCardOcrVo.setBirthday(DateTimeFormat.forPattern("yyyy/MM/dd").parseDateTime(resp.getBirth()).toDate());
                idCardOcrVo.setIdcardNo(resp.getIdNum());
                idCardOcrVo.setIdcardAddress(resp.getAddress());
                //上传身份证正面图片到腾讯云cos
                CosUploadVo cosUploadVo = cosService.upload(file, "idCard");
                idCardOcrVo.setIdcardFrontUrl(cosUploadVo.getUrl());
                idCardOcrVo.setIdcardFrontShowUrl(cosUploadVo.getShowUrl());
            } else {
                //身份证反面
                //证件有效期："2010.07.21-2020.07.21"
                String idcardExpireString = resp.getValidDate().split("-")[1];
                idCardOcrVo.setIdcardExpire(DateTimeFormat.forPattern("yyyy.MM.dd").parseDateTime(idcardExpireString).toDate());
                //上传身份证反面图片到腾讯云cos
                CosUploadVo cosUploadVo = cosService.upload(file, "idCard");
                idCardOcrVo.setIdcardBackUrl(cosUploadVo.getUrl());
                idCardOcrVo.setIdcardBackShowUrl(cosUploadVo.getShowUrl());
            }
            return idCardOcrVo;
        } catch (Exception e) {
            throw new GlobalException(ResultCodeEnum.DATA_ERROR);
        }
    }

    /**
     * 驾驶证识别
     *
     * @param file
     * @return
     */
    @Override
    public DriverLicenseOcrVo driverLicenseOcr(MultipartFile file) {
        try {
            byte[] base64 = Base64.encodeBase64(file.getBytes());
            String file64 = new String(base64);
            OcrClient client = getOcrClient();
            // 实例化一个请求对象,每个接口都会对应一个request对象
            DriverLicenseOCRRequest req = new DriverLicenseOCRRequest();
            req.setImageBase64(file64);
            // 返回的resp是一个DriverLicenseOCRResponse的实例，与请求对象对应
            DriverLicenseOCRResponse resp = client.DriverLicenseOCR(req);
            DriverLicenseOcrVo driverLicenseOcrVo = new DriverLicenseOcrVo();
            if (StringUtils.hasText(resp.getName())) {
                //驾驶证正面
                //驾驶证名称要与身份证名称一致
                driverLicenseOcrVo.setName(resp.getName());
                driverLicenseOcrVo.setDriverLicenseClazz(resp.getClass_());
                driverLicenseOcrVo.setDriverLicenseNo(resp.getCardCode());
                driverLicenseOcrVo.setDriverLicenseIssueDate(DateTimeFormat.forPattern("yyyy-MM-dd").parseDateTime(resp.getDateOfFirstIssue()).toDate());
                driverLicenseOcrVo.setDriverLicenseExpire(DateTimeFormat.forPattern("yyyy-MM-dd").parseDateTime(resp.getEndDate()).toDate());
                //上传驾驶证正面图片到腾讯云cos
                CosUploadVo cosUploadVo = cosService.upload(file, "driverLicense");
                driverLicenseOcrVo.setDriverLicenseFrontUrl(cosUploadVo.getUrl());
                driverLicenseOcrVo.setDriverLicenseFrontShowUrl(cosUploadVo.getShowUrl());
            } else {
                //驾驶证反面
                //上传驾驶证反面图片到腾讯云cos
                CosUploadVo cosUploadVo = cosService.upload(file, "driverLicense");
                driverLicenseOcrVo.setDriverLicenseBackUrl(cosUploadVo.getUrl());
                driverLicenseOcrVo.setDriverLicenseBackShowUrl(cosUploadVo.getShowUrl());
            }
            return driverLicenseOcrVo;
        } catch (Exception e) {
            throw new GlobalException(ResultCodeEnum.DATA_ERROR);
        }

    }

    /**
     * 获取Ocr认证客户端
     *
     * @return
     */
    private OcrClient getOcrClient() {
        // 实例化一个认证对象，入参需要传入腾讯云账户 SecretId 和 SecretKey，此处还需注意密钥对的保密
        // 代码泄露可能会导致 SecretId 和 SecretKey 泄露，并威胁账号下所有资源的安全性。以下代码示例仅供参考，建议采用更安全的方式来使用密钥，请参见：https://cloud.tencent.com/document/product/1278/85305
        // 密钥可前往官网控制台 https://console.cloud.tencent.com/cam/capi 进行获取
        Credential cred = new Credential(tencentCloudProperties.getSecretId(), tencentCloudProperties.getSecretKey());
        // 实例化一个http选项，可选的，没有特殊需求可以跳过
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("ocr.tencentcloudapi.com");
        // 实例化一个client选项，可选的，没有特殊需求可以跳过
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        // 实例化要请求产品的client对象,clientProfile是可选的
        return new OcrClient(cred, tencentCloudProperties.getRegion(), clientProfile);
    }
}
