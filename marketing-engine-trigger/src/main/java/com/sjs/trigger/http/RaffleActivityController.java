package com.sjs.trigger.http;

import com.alibaba.fastjson.JSON;
import com.sjs.api.dto.ActivityDrawRequestDTO;
import com.sjs.api.dto.ActivityDrawResponseDTO;
import com.sjs.api.IRaffleActivityService;
import com.sjs.api.response.Response;
import com.sjs.domain.activity.model.entity.UserRaffleOrderEntity;
import com.sjs.domain.activity.service.IRaffleActivityPartakeService;
import com.sjs.domain.activity.service.armory.IActivityArmory;
import com.sjs.domain.award.model.entity.UserAwardRecordEntity;
import com.sjs.domain.award.model.valobj.AwardStateVO;
import com.sjs.domain.award.service.IAwardService;
import com.sjs.domain.rebate.model.entity.BehaviorEntity;
import com.sjs.domain.rebate.model.valobj.BehaviorTypeVO;
import com.sjs.domain.rebate.service.IBehaviorRebateService;
import com.sjs.domain.strategy.model.entity.RaffleAwardEntity;
import com.sjs.domain.strategy.model.entity.RaffleFactorEntity;
import com.sjs.domain.strategy.service.IRaffleStrategy;
import com.sjs.domain.strategy.service.armory.IStrategyArmory;
import com.sjs.types.enums.ResponseCode;
import com.sjs.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;


@Slf4j
@RestController()
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/raffle/activity/")
public class RaffleActivityController implements IRaffleActivityService {

    @Resource
    private IActivityArmory activityArmory;
    @Resource
    private IStrategyArmory strategyArmory;
    @Resource
    private IRaffleStrategy raffleStrategy;
    @Resource
    private IRaffleActivityPartakeService activityPartakeService;
    @Resource
    private IAwardService awardService;
    @Resource
    private IBehaviorRebateService behaviorRebateService;

    private final SimpleDateFormat dateFormatDay = new SimpleDateFormat("yyyy-MM-dd");

    @GetMapping("armory")
    @Override
    public Response<Boolean> armory(Long activityId) {
        try{
            log.info("活动预热 activityId:{}", activityId);

            //1.进行活动预热
            activityArmory.assembleActivityByActivityId(activityId);
            //2.进行策略预热
            strategyArmory.assembleLotteryStrategyByActivityId(activityId);

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("活动预热失败 activityId：{}", activityId, e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @PostMapping("draw")
    @Override
    public Response<ActivityDrawResponseDTO> draw(@RequestBody ActivityDrawRequestDTO requestDTO) {
        try{
            log.info("开始抽奖 activityId:{}", requestDTO.getActivityId());

            //1.参数校验
            String userId = requestDTO.getUserId();
            Long activityId = requestDTO.getActivityId();
            if(null == activityId || StringUtils.isBlank(userId)){
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }

            //2.参与活动 - 创建参与记录订单
            UserRaffleOrderEntity userRaffleOrderEntity = activityPartakeService.createOrder(userId, activityId);
            log.info("活动抽奖，创建订单 userId:{} activityId:{} orderId:{}", requestDTO.getUserId(), requestDTO.getActivityId(), userRaffleOrderEntity.getOrderId());

            //3.抽奖策略 - 执行抽奖
            RaffleAwardEntity raffleAwardEntity = raffleStrategy.performRaffle(RaffleFactorEntity.builder()
                    .userId(userId)
                    .strategyId(userRaffleOrderEntity.getStrategyId())
                    .endDateTime(userRaffleOrderEntity.getEndDateTime())
                    .build());
            log.info("活动抽奖，执行抽奖 userId:{} activityId:{} strategyId:{}", requestDTO.getUserId(), requestDTO.getActivityId(), userRaffleOrderEntity.getStrategyId());

            //4.存放结果 - 写入中奖记录
            UserAwardRecordEntity userAwardRecordEntity = new UserAwardRecordEntity();
            userAwardRecordEntity.setUserId(userId);
            userAwardRecordEntity.setActivityId(activityId);
            userAwardRecordEntity.setStrategyId(userRaffleOrderEntity.getStrategyId());
            userAwardRecordEntity.setOrderId(userRaffleOrderEntity.getOrderId());
            userAwardRecordEntity.setAwardId(raffleAwardEntity.getAwardId());
            userAwardRecordEntity.setAwardTitle(raffleAwardEntity.getAwardTitle());
            userAwardRecordEntity.setAwardTime(new Date());
            userAwardRecordEntity.setAwardState(AwardStateVO.create);

            awardService.saveUserAwardRecord(userAwardRecordEntity);
            log.info("活动抽奖，写入中奖记录 userId:{} activityId:{} strategyId:{}", requestDTO.getUserId(), requestDTO.getActivityId(), userRaffleOrderEntity.getStrategyId());

            //5.返回结果
            return Response.<ActivityDrawResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ActivityDrawResponseDTO.builder()
                            .awardId(raffleAwardEntity.getAwardId())
                            .awardTitle(raffleAwardEntity.getAwardTitle())
                            .awardIndex(raffleAwardEntity.getSort())
                            .build())
                    .build();
        } catch (AppException e) {
            log.error("活动抽奖失败 userId:{} activityId:{}", requestDTO.getUserId(), requestDTO.getActivityId(), e);
            return Response.<ActivityDrawResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        }
        catch (Exception e) {
            log.error("随机抽奖失败 activityId：{}", requestDTO.getActivityId(), e);
            return Response.<ActivityDrawResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }



    @PostMapping("calendar_sign_rebate")
    @Override
    public Response<Boolean> calendarSignRebate(@RequestParam String userId) {
        try{
            log.info("用户行为返利 - 日签奖励 userId:{}", userId);

            BehaviorEntity behaviorEntity = new BehaviorEntity();
            behaviorEntity.setUserId(userId);
            behaviorEntity.setBehaviorTypeVO(BehaviorTypeVO.SIGN);
            behaviorEntity.setOutBusinessNo(dateFormatDay.format(new Date()));

            List<String> orderIds = behaviorRebateService.createOrder(behaviorEntity);
            log.info("日历签到返利完成 userId:{} orderIds: {}", userId, JSON.toJSONString(orderIds));

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();
        } catch (AppException e) {
            log.error("日历签到返利异常 userId:{} ", userId, e);
            return Response.<Boolean>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("日历签到返利失败 userId:{}", userId);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }
}
