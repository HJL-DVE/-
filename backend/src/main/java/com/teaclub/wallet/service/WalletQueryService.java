package com.teaclub.wallet.service;

import com.teaclub.common.api.PageResult;
import com.teaclub.wallet.dto.TransactionQuery;
import com.teaclub.wallet.dto.WalletQuery;
import com.teaclub.wallet.vo.WalletTransactionVO;
import com.teaclub.wallet.vo.WalletVO;

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
}
