package com.atguigu.daijia.coupon.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GlobalException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.coupon.mapper.CouponInfoMapper;
import com.atguigu.daijia.coupon.mapper.CustomerCouponMapper;
import com.atguigu.daijia.coupon.service.CouponInfoService;
import com.atguigu.daijia.model.entity.coupon.CouponInfo;
import com.atguigu.daijia.model.entity.coupon.CustomerCoupon;
import com.atguigu.daijia.model.form.coupon.UseCouponForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.coupon.AvailableCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoReceiveCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoUseCouponVo;
import com.atguigu.daijia.model.vo.coupon.UsedCouponVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service

public class CouponInfoServiceImpl extends ServiceImpl<CouponInfoMapper, CouponInfo> implements CouponInfoService {

    @Resource
    private CouponInfoMapper couponInfoMapper;
    @Resource
    private CustomerCouponMapper customerCouponMapper;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 查询未领取优惠券分页列表
     *
     * @param pageParam
     * @param customerId
     * @return
     */
    @Override
    public PageVo<NoReceiveCouponVo> findNoReceivePage(Page<CouponInfo> pageParam, Long customerId) {
        IPage<NoReceiveCouponVo> pageInfo = couponInfoMapper.findNoReceivePage(pageParam, customerId);
        return new PageVo<>(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    /**
     * 查询未使用优惠券分页列表
     *
     * @param pageParam
     * @param customerId
     * @return
     */
    @Override
    public PageVo<NoUseCouponVo> findNoUsePage(Page<CouponInfo> pageParam, Long customerId) {
        IPage<NoUseCouponVo> pageInfo = couponInfoMapper.findNoUsePage(pageParam, customerId);
        return new PageVo<>(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    /**
     * 查询已使用优惠券分页列表
     *
     * @param pageParam
     * @param customerId
     * @return
     */
    @Override
    public PageVo<UsedCouponVo> findUsedPage(Page<CouponInfo> pageParam, Long customerId) {
        IPage<UsedCouponVo> pageInfo = couponInfoMapper.findUsedPage(pageParam, customerId);
        return new PageVo<>(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    /**
     * 领取优惠券
     *
     * @param customerId
     * @param couponId
     * @return
     */
    @Override
    public Boolean receive(Long customerId, Long couponId) {
        //1.couponId查询优惠卷信息
        CouponInfo couponInfo = couponInfoMapper.selectById(couponId);
        //2.判断优惠卷是否存在
        if (couponInfo == null) {
            throw new GlobalException(ResultCodeEnum.DATA_ERROR);
        }
        //3.判断优惠卷是否过期
        if (couponInfo.getExpireTime().before(new Date())) {
            throw new GlobalException(ResultCodeEnum.COUPON_EXPIRE);
        }
        //4.检查库存，发行数量和领取数量
        if (couponInfo.getPublishCount() != 0 && couponInfo.getReceiveCount() >= couponInfo.getPublishCount()) {
            throw new GlobalException(ResultCodeEnum.COUPON_LESS);
        }
        RLock lock = null;
        try {
            // 初始化分布式锁
            //每人领取限制  与 优惠券发行总数 必须保证原子性，使用customerId减少锁的粒度，增加并发能力
            lock = redissonClient.getLock(RedisConstant.COUPON_LOCK + customerId);
            boolean result = lock.tryLock(RedisConstant.COUPON_LOCK_WAIT_TIME, RedisConstant.COUPON_LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (result) {
                //5.检验每人限制领取数量
                if (couponInfo.getPerLimit() > 0) {
                    Long count = customerCouponMapper.selectCount(new LambdaQueryWrapper<CustomerCoupon>()
                            .eq(CustomerCoupon::getCouponId, couponId)
                            .eq(CustomerCoupon::getCustomerId, customerId));
                    if (count >= couponInfo.getPerLimit()) {
                        throw new GlobalException(ResultCodeEnum.COUPON_USER_LIMIT);
                    }
                }
                //6.领取优惠卷 更新领取数量
                int row = couponInfoMapper.updateReceiveCount(couponId);
                if (row == 1) {
                    //添加领取记录
                    CustomerCoupon customerCoupon = new CustomerCoupon();
                    customerCoupon.setCustomerId(customerId);
                    customerCoupon.setCouponId(couponId);
                    customerCoupon.setStatus(1);
                    customerCoupon.setReceiveTime(new Date());
                    customerCoupon.setExpireTime(couponInfo.getExpireTime());
                    customerCouponMapper.insert(customerCoupon);
                    return true;
                }else{
                    throw new GlobalException(ResultCodeEnum.COUPON_LESS);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }
        throw new GlobalException(ResultCodeEnum.COUPON_FAIL);
    }

    /**
     * 获取未使用的最佳优惠券信息
     * @param customerId
     * @param orderAmount
     * @return
     */
    @Override
    public List<AvailableCouponVo> findAvailableCoupon(Long customerId, BigDecimal orderAmount) {
        //1.创建一个list集合存储最终返回的数据
        List<AvailableCouponVo> availableCouponVoList = new ArrayList<>();
        //2.根据乘客id，获取已经领取但是没有使用的优惠卷列表
        List<NoUseCouponVo> noUseCouponList = couponInfoMapper.findNoUseList(customerId);
        //3.遍历优惠卷列表,判断优惠卷类型:现金卷和折扣卷
        //3.1 现金卷
        List<NoUseCouponVo> cashList = noUseCouponList.stream().filter(item -> item.getCouponType() == 1).toList();
        cashList.forEach(coupon->{
            //判断现金卷是否满足条件
            BigDecimal reduceAmount = coupon.getAmount();
            if(coupon.getConditionAmount().doubleValue()==0 && orderAmount.subtract(reduceAmount).doubleValue()>0){
                //没有门槛==0,订单金额必须大于优惠减免金额
                availableCouponVoList.add(this.buildBestNoUseCouponVo(coupon,reduceAmount));
            }else if (coupon.getConditionAmount().doubleValue()>0 && orderAmount.subtract(coupon.getConditionAmount()).doubleValue()>0){
                //有门槛，订单金额大于优惠门槛金额
                availableCouponVoList.add(this.buildBestNoUseCouponVo(coupon,reduceAmount));
            }

        });
        //3.2 折扣卷
        List<NoUseCouponVo> discountList = noUseCouponList.stream().filter(item -> item.getCouponType() == 2).toList();
        //判断折扣卷是否满足条件
        discountList.forEach(coupon->{
            BigDecimal discount = coupon.getDiscount();
            //折扣之后的金额
            BigDecimal discountAmount = orderAmount.multiply(discount).divide(new BigDecimal("10")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal reduceAmount = orderAmount.subtract(discountAmount);
            //2.2.1.没门槛
            if (coupon.getConditionAmount().doubleValue() == 0) {
                availableCouponVoList.add(this.buildBestNoUseCouponVo(coupon, reduceAmount));
            }
            //2.2.2.有门槛，订单折扣后金额大于优惠券门槛金额
            if (coupon.getConditionAmount().doubleValue() > 0 && discountAmount.subtract(coupon.getConditionAmount()).doubleValue() > 0) {
                availableCouponVoList.add(this.buildBestNoUseCouponVo(coupon, reduceAmount));
            }
        });
        //4.把满足条件的优惠卷放到list集合中
        //根据金额排序
        if (!CollectionUtils.isEmpty(availableCouponVoList)) {
            availableCouponVoList.sort(new Comparator<AvailableCouponVo>() {
                @Override
                public int compare(AvailableCouponVo o1, AvailableCouponVo o2) {
                    return o1.getReduceAmount().compareTo(o2.getReduceAmount());
                }
            });
        }
        return availableCouponVoList;
    }

    private AvailableCouponVo buildBestNoUseCouponVo(NoUseCouponVo cash,BigDecimal reduceAmount) {
        AvailableCouponVo bestNoUseCouponVo = new AvailableCouponVo();
        BeanUtils.copyProperties(cash, bestNoUseCouponVo);
        bestNoUseCouponVo.setCouponId(cash.getId());
        bestNoUseCouponVo.setReduceAmount(reduceAmount);
        return bestNoUseCouponVo;
    }

    /**
     * 使用优惠券
     * @param useCouponForm
     * @return
     */
    @Override
    public BigDecimal useCoupon(UseCouponForm useCouponForm) {
        //1.根据乘客优惠券id获取乘客优惠劵信息
        CustomerCoupon customerCoupon = customerCouponMapper.selectById(useCouponForm.getCustomerCouponId());
        if(customerCoupon==null){
            throw new GlobalException(ResultCodeEnum.ARGUMENT_VALID_ERROR);
        }
        //2.根据优惠劵id获取优惠劵信息
        CouponInfo couponInfo = couponInfoMapper.selectById(customerCoupon.getCouponId());
        if(couponInfo==null){
            throw new GlobalException(ResultCodeEnum.ARGUMENT_VALID_ERROR);
        }
        //3.判断优惠劵是否是当前乘客的优惠劵
        if(customerCoupon.getCustomerId()!=useCouponForm.getCustomerId()){
            throw new GlobalException(ResultCodeEnum.ILLEGAL_REQUEST);
        }
        //4.判断是否具备优惠卷使用条件
        //4.1 现金
        BigDecimal reduceAmount = new BigDecimal("0");
        if(couponInfo.getCouponType()==1){
            //没有门槛，订单金额大于减免金额
            //有门槛，订单金额大于优惠劵门槛金额
            if(couponInfo.getConditionAmount().doubleValue()==0&&useCouponForm.getOrderAmount().subtract(couponInfo.getAmount()).doubleValue()>0){
                reduceAmount = couponInfo.getAmount();
            }else if (couponInfo.getConditionAmount().doubleValue()>0 && useCouponForm.getOrderAmount().subtract(couponInfo.getConditionAmount()).doubleValue()>0)
                reduceAmount = couponInfo.getAmount();
        }else{
            //4.2 折扣
            BigDecimal discountAmount = useCouponForm.getOrderAmount().multiply(couponInfo.getDiscount()).divide(new BigDecimal("10")).setScale(2, RoundingMode.HALF_UP);
            //没门槛
            if (couponInfo.getConditionAmount().doubleValue() == 0) {
                reduceAmount=useCouponForm.getOrderAmount().subtract(discountAmount);
            }
            //有门槛，订单折扣后金额大于优惠券门槛金额
            if (couponInfo.getConditionAmount().doubleValue() > 0 && discountAmount.subtract(couponInfo.getConditionAmount()).doubleValue() > 0) {
                reduceAmount=useCouponForm.getOrderAmount().subtract(discountAmount);
            }
        }
        //5.如果满足条件，更新数据
        if(reduceAmount.doubleValue()>0){
            //更新coupon_info使用的数量
            couponInfo.setUseCount(couponInfo.getUseCount()+1);
            couponInfoMapper.updateById(couponInfo);
            //更新customer_coupon
            CustomerCoupon updateCustomerCoupon = new CustomerCoupon();
            updateCustomerCoupon.setId(customerCoupon.getId());
            updateCustomerCoupon.setUsedTime(new Date());
            updateCustomerCoupon.setOrderId(useCouponForm.getOrderId());
            customerCouponMapper.updateById(updateCustomerCoupon);
            return reduceAmount;
        }
        throw new GlobalException(ResultCodeEnum.DATA_ERROR);
    }
}
