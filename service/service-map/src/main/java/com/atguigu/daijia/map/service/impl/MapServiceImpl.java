package com.atguigu.daijia.map.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.daijia.common.execption.GlobalException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.map.config.TencentCloudProperties;
import com.atguigu.daijia.map.service.MapService;
import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class MapServiceImpl implements MapService {
    @Resource
    private RestTemplate restTemplate;
    @Resource
    private TencentCloudProperties tencentCloudProperties;

    /**
     * 计算驾驶线路
     * @param calculateDrivingLineForm
     * @return
     */
    @Override
    public DrivingLineVo calculateDrivingLine(CalculateDrivingLineForm calculateDrivingLineForm) {
        //1.定义调用地址
        String url = "https://apis.map.qq.com/ws/direction/v1/driving/?from={from}&to={to}&key={key}";
        //2.封装传递参数
        Map<String, String> map = new HashMap<>();
        map.put("from",calculateDrivingLineForm.getStartPointLatitude()+","+calculateDrivingLineForm.getStartPointLongitude());
        map.put("to",calculateDrivingLineForm.getEndPointLatitude()+","+calculateDrivingLineForm.getEndPointLongitude());
        map.put("key",tencentCloudProperties.getKey());
        //3.使用RestTemplate进行调用
        JSONObject result = restTemplate.getForObject(url, JSONObject.class, map);
        //4.处理返回结果
        if(result!=null){
            //调用失败
            if(result.getIntValue("status")!=0){
                throw new GlobalException(ResultCodeEnum.MAP_FAIL);
            }
            //获取返回路线信息
            JSONObject route = result.getJSONObject("result").getJSONArray("routes").getJSONObject(0);
            DrivingLineVo drivingLineVo = new DrivingLineVo();
            drivingLineVo.setDistance(route.getBigDecimal("distance")
                    .divide(new BigDecimal(1000))
                    .setScale(2, RoundingMode.HALF_UP));
            drivingLineVo.setDuration(route.getBigDecimal("duration"));
            drivingLineVo.setPolyline(route.getJSONArray("polyline"));
            return drivingLineVo;
        }
        return null;
    }
}
