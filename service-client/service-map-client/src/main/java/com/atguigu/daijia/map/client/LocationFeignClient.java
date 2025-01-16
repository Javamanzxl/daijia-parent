package com.atguigu.daijia.map.client;

import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.model.form.map.UpdateDriverLocationForm;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "service-map")
public interface LocationFeignClient {

    @PostMapping("/map/location/updateDriverLocation")
    Result<Boolean> updateDriverLocation(@RequestBody UpdateDriverLocationForm updateDriverLocationForm);
    @DeleteMapping("/map/location/removeDriverLocation/{driverId}")
    Result<Boolean> removeDriverLocation(@PathVariable Long driverId);
}