package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;



    @Override
    public Result seckillVoucher(Long voucherId) {

        // 1. 查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        // 2. 判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀尚未开始");
        }

        // 3. 判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 秒杀已经结束
            return Result.fail("秒杀已经结束");
        }

        // 4. 判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足");
        }
        // 用户id
        Long userId = UserHolder.getUser().getId();

        synchronized (userId.toString().intern()) {
            // 获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result createVoucherOrder(Long voucherId) {
        // 用户id
        Long userId = UserHolder.getUser().getId();



            // 查询订单
            long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

            // 判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                return Result.fail("用户已经购买过了");
            }

            // 5. 扣减库存
            // 乐观锁解决方案
            boolean success = seckillVoucherService
                    .update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
//                .eq("stock",voucher.getStock())
                    .gt("stock", 0)
                    .update();

            if (!success) {
                // 扣减失败
                return Result.fail("库存不足");
            }
            // 6. 创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            // 订单id
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);

            voucherOrder.setUserId(userId);
            // 代金券id
            voucherOrder.setVoucherId(voucherId);

            save(voucherOrder);

            // 返回订单id
            return Result.ok(voucherOrder);
        }

}
