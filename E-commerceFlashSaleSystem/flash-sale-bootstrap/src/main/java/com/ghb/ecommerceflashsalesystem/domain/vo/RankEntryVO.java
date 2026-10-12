package com.ghb.ecommerceflashsalesystem.domain.vo;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankEntryVO {
    private Integer rank;  //等级
    private Long userId;
    private Long score;      // 毫秒时间戳
    private String orderNo;
}
