package com.sjs.marketing.infrastructure.dao;

import com.sjs.marketing.infrastructure.dao.po.PayCallbackLogPO;
import com.sjs.marketing.infrastructure.dao.po.PayOrderPO;
import com.sjs.marketing.infrastructure.dao.po.PayReconcileDiffPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 支付订单与回调日志的数据访问。
 *
 * <p>pay_order / pay_callback_log 都按 {@code user_id} 分库，
 * 带 user_id 的查询精准路由，超时扫单这类任务不带分片键、广播各分片。</p>
 *
 * @author marketing-engine contributors
 */
@Mapper
public interface IPayDao {

    int insertOrder(PayOrderPO order);

    /** 不带分片键（下单幂等检查时还不知道落哪个库，按 order_no 广播查） */
    PayOrderPO queryOrderByNo(@Param("orderNo") String orderNo);

    PayOrderPO queryOrder(@Param("orderNo") String orderNo, @Param("userId") String userId);

    /** 条件更新：只有 created 才能变 paid */
    int markPaid(@Param("orderNo") String orderNo, @Param("userId") String userId,
                 @Param("outTradeNo") String outTradeNo);

    /** 条件更新：只有 paid 才能变 granted */
    int markGranted(@Param("orderNo") String orderNo, @Param("userId") String userId);

    /** 条件更新：只有 created 才能变 closed */
    int markClosed(@Param("orderNo") String orderNo, @Param("userId") String userId);

    /** 退款：paid/granted → refunded，累加已退金额 */
    int markRefunded(@Param("orderNo") String orderNo, @Param("userId") String userId,
                     @Param("refundedCent") int refundedCent);

    /** 对账扫单：按状态取一批订单（不带分片键，广播各分片） */
    List<PayOrderPO> queryOrdersByState(@Param("state") String state, @Param("limit") int limit);

    List<PayOrderPO> queryTimeoutOrders(@Param("deadline") Date deadline, @Param("limit") int limit);

    int insertCallbackLog(PayCallbackLogPO log);

    List<PayCallbackLogPO> queryCallbackLogs(@Param("orderNo") String orderNo, @Param("limit") int limit);

    // ---------- 支付三方对账 ----------

    int insertReconcileDiff(PayReconcileDiffPO diff);

    List<PayReconcileDiffPO> queryReconcileDiffs(@Param("userId") String userId, @Param("limit") int limit);
}
