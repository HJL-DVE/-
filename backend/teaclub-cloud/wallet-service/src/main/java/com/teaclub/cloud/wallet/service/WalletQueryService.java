package com.teaclub.cloud.wallet.service;

import com.teaclub.cloud.core.api.PageResult;
import com.teaclub.cloud.wallet.dto.TransactionQuery;
import com.teaclub.cloud.wallet.dto.WalletQuery;
import com.teaclub.cloud.wallet.vo.WalletStatsVO;
import com.teaclub.cloud.wallet.vo.WalletTransactionVO;
import com.teaclub.cloud.wallet.vo.WalletVO;

import java.util.List;
import java.util.Map;

public interface WalletQueryService {

    /** 钱包分页（受门店数据权限约束） */
    PageResult<WalletVO> pageWallet(WalletQuery query);

    /** 按会员查钱包 */
    WalletVO getByMember(Long memberId);

    /** 流水分页（受门店数据权限约束） */
    PageResult<WalletTransactionVO> pageTransaction(TransactionQuery query);

    /** 某会员的流水 */
    List<WalletTransactionVO> listByMember(Long memberId, int limit);

    /** 对账：余额与流水不一致的钱包 */
    List<Map<String, Object>> reconcile();

    /** 仪表盘统计（真实数据聚合，受门店数据权限约束） */
    WalletStatsVO stats();
}
