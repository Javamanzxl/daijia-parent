package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.execption.GlobalException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.client.OcrFeignClient;
import com.atguigu.daijia.driver.service.OcrService;
import com.atguigu.daijia.model.vo.driver.DriverLicenseOcrVo;
import com.atguigu.daijia.model.vo.driver.IdCardOcrVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class OcrServiceImpl implements OcrService {

    @Resource
    private OcrFeignClient ocrFeignClient;
    /**
     * 身份证识别
     * @param file
     * @return
     */
    @Override
    public IdCardOcrVo idCardOcr(MultipartFile file) {
        Result<IdCardOcrVo> result = ocrFeignClient.idCardOcr(file);
        if(result.getCode()!=200){
            throw new GlobalException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }

    /**
     * 驾驶证识别
     * @param file
     * @return
     */
    @Override
    public DriverLicenseOcrVo driverLicenseOcr(MultipartFile file) {
        Result<DriverLicenseOcrVo> result = ocrFeignClient.driverLicenseOcr(file);
        if(result.getCode()!=200){
            throw new GlobalException(ResultCodeEnum.FEIGN_FAIL);
        }
        return result.getData();
    }
}
