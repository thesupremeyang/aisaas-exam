package com.hxh.apboa.scheduler.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.common.entity.JobLog;
import com.hxh.apboa.scheduler.mapper.JobLogMapper;
import org.springframework.stereotype.Service;

/**
 * @author huxuehao
 **/
@Service
public class QuartzLogServiceImpl extends ServiceImpl<JobLogMapper, JobLog> implements QuartzLogService {
}
