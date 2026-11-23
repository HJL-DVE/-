package com.teaclub.cloud.wallet.service;

import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.wallet.dto.CardRefundApplyRequest;
import com.teaclub.cloud.wallet.dto.CardRefundAuditRequest;
import com.teaclub.cloud.wallet.vo.CardRefundVO;

import java.util.List;

/**
 * 会员退卡（储值卡注销退余额）。
 *
 * <p>资金规则：<b>实付本金可退，赠送金额作废不折现</b>。
 * 流程为「会员申请 → 后台审核 → 扣回余额并落资金流水」，
 * 审核前余额仍可正常消费，审核时再校验能否足额扣回。
 */
public interface CardRefundService {

    /** 会员提交退卡申请；已有待审核申请时直接返回该申请（幂等） */
    CardRefundVO apply(Long memberId, CardRefundApplyRequest request);

    /** 会员查看自己的退卡申请记录 */
    List<CardRefundVO> myApplications(Long memberId);

    /** 后台分页查看退卡申请 */
    PageResult<CardRefundVO> page(Integer pageNo, Integer pageSize, String status, Long memberId);

    /** 后台审核：通过则扣回余额、作废赠送金并落资金流水 */
    CardRefundVO audit(Long id, CardRefundAuditRequest request);
}
