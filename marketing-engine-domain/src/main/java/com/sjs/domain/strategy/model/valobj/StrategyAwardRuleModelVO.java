package com.sjs.domain.strategy.model.valobj;

import com.sjs.domain.strategy.service.rule.filter.factory.DefaultLogicFactory;
import com.sjs.types.common.Constants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StrategyAwardRuleModelVO {
    String ruleModels;

    public String[] raffleCenterRuleModelList(){
        if(ruleModels == null) return null;

        String[] ruleValues = ruleModels.split(Constants.SPLIT);

        List<String> ruleModelList = new ArrayList<>();
        for (String ruleValue : ruleValues) {
            if(DefaultLogicFactory.LogicModel.isCenter(ruleValue)){
                ruleModelList.add(ruleValue);
            }
        }
        return ruleModelList.toArray(new String[0]);
    }

    public String[] raffleAfterRuleModelList(){
        String[] ruleValues = ruleModels.split(Constants.SPLIT);

        List<String> ruleModelList = new ArrayList<>();
        for (String ruleValue : ruleValues) {
            if(DefaultLogicFactory.LogicModel.isAfter(ruleValue)){
                ruleModelList.add(ruleValue);
            }
        }
        return ruleModelList.toArray(new String[0]);
    }
}
