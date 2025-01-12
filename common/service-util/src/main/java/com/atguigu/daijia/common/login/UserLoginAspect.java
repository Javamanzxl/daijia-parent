package com.atguigu.daijia.common.login;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GlobalException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.util.AuthContextHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * @author ：zxl
 * @Description: 用户登陆校验切面类
 * @ClassName: UserLoginAspect
 * @date ：2025/01/12 20:13
 */
@Component
@Aspect
public class UserLoginAspect {
    @Resource
    private RedisTemplate<String,String> redisTemplate;
    @Around("execution(* com.atguigu.daijia.*.controller.*.*(..)) && @annotation(userLogin)")
    public Object login(ProceedingJoinPoint point,UserLogin userLogin) throws Throwable {

        //1.从请求头中获取token
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if(attributes==null){
            throw new GlobalException(ResultCodeEnum.NO_TOKEN);
        }
        String token = attributes.getRequest().getHeader("token");
        //2.判断token是否为空，如果为空，返回登陆提示
        if(!StringUtils.hasText(token)){
            throw new GlobalException(ResultCodeEnum.LOGIN_AUTH);
        }
        //3.如果不为空，查询redis
        String customerId = redisTemplate.opsForValue().get(RedisConstant.USER_LOGIN_KEY_PREFIX + token);
        //4.查询redis对应用户id,把用户id放到ThreadLocal里面
        if(StringUtils.hasText(customerId)){
            AuthContextHolder.setUserId(Long.parseLong(customerId));
        }
        //5.执行业务方法
        return point.proceed();

    }
}
