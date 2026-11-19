package com.teaclub.wallet.service;

import com.teaclub.wallet.dto.RechargePlanSaveRequest;
import com.teaclub.wallet.vo.RechargePlanVO;

import java.util.List;

public interface RechargePlanService {

    /** 管理端：全部方案 */
    List<RechargePlanVO> listAll(String status);

    /** 会员端：某门店下当前会员可用的方案 */
    List<RechargePlanVO> listAvailable(Long storeId, Long memberId);

    RechargePlanVO detail(Long id);

    Long create(RechargePlanSaveRequest request);

    void update(Long id, RechargePlanSaveRequest request);

    void delete(Long id);
}
