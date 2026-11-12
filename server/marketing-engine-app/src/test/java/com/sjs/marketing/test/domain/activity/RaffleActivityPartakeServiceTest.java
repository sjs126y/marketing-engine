package com.sjs.marketing.test.domain.activity;

import com.sjs.marketing.types.util.JsonUtils;

import com.sjs.marketing.domain.activity.model.entity.PartakeRaffleActivityEntity;
import com.sjs.marketing.domain.activity.model.entity.UserRaffleOrderEntity;
import com.sjs.marketing.domain.activity.service.IRaffleActivityPartakeService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;

/**
 * @author marketing-engine contributors
 * @description 抽奖活动订单单测
 * @create 2024-03-16 11:51
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RaffleActivityPartakeServiceTest {

    @Resource
    private IRaffleActivityPartakeService raffleActivityPartakeService;

    @Test
    public void test_createOrder() {
        // 请求参数
        PartakeRaffleActivityEntity partakeRaffleActivityEntity = new PartakeRaffleActivityEntity();
        partakeRaffleActivityEntity.setUserId("demo-user");
        partakeRaffleActivityEntity.setActivityId(100301L);
        // 调用接口
        UserRaffleOrderEntity userRaffleOrder = raffleActivityPartakeService.createOrder(partakeRaffleActivityEntity);
        log.info("请求参数：{}", JsonUtils.toJson(partakeRaffleActivityEntity));
        log.info("测试结果：{}", JsonUtils.toJson(userRaffleOrder));
    }

}
