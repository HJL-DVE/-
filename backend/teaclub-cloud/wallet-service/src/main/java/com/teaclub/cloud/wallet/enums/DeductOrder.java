package com.teaclub.cloud.wallet.enums;

/**
 * 余额扣款顺序。
 *
 * <p>明确按顺序扣减，<b>禁止按比例分摊</b>——比例分摊会让退款无法精确还原资金构成。
 */
public enum DeductOrder {

    /** 先扣赠送金，再扣实付金（默认，对会员更有利） */
    GIFT_FIRST,

    /** 先扣实付金，再扣赠送金 */
    PRINCIPAL_FIRST;

    public static DeductOrder of(String value) {
        if (value == null) {
            return GIFT_FIRST;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return GIFT_FIRST;
        }
    }
}
