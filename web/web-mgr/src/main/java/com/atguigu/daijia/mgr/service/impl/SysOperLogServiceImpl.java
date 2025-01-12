package com.atguigu.daijia.mgr.service.impl;

import com.atguigu.daijia.mgr.service.SysOperLogService;
import com.atguigu.daijia.model.entity.system.SysOperLog;
import com.atguigu.daijia.model.query.system.SysOperLogQuery;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.system.client.SysOperLogFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service

public class SysOperLogServiceImpl implements SysOperLogService {

	@Resource
	private SysOperLogFeignClient sysOperLogFeignClient;

	@Override
	public PageVo<SysOperLog> findPage(Long page, Long limit, SysOperLogQuery sysOperLogQuery) {
		return sysOperLogFeignClient.findPage(page, limit, sysOperLogQuery).getData();
	}

	@Override
	public void saveSysLog(SysOperLog sysOperLog) {
		sysOperLogFeignClient.saveSysLog(sysOperLog);
	}

	@Override
	public SysOperLog getById(Long id) {
		return sysOperLogFeignClient.getById(id).getData();
	}
}
