package com.teaclub.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.member.entity.MemMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface MemMemberMapper extends BaseMapper<MemMember> {

    /**
     * 查询在指定门店范围内"可见"的会员 ID。
     *
     * <p>可见口径（B 方案）：
     * <ul>
     *   <li>注册门店属于授权门店（会员归属），或</li>
     *   <li>在该授权门店下有消费订单（会员来过）</li>
     * </ul>
     *
     * <p>为什么需要后者：会员属于品牌而非门店，会员在 A 店注册后到 B 店消费时，
     * B 店收银台必须能识别该会员，否则无法核验身份、使用权益。
     * 若只看注册门店，会出现"看得到订单却查不到人"的矛盾。
     *
     * @param brandId  品牌 ID
     * @param storeIds 授权门店 ID 集合（调用方需保证非空）
     */
    @Select("""
            <script>
            SELECT DISTINCT m.id
            FROM mem_member m
            WHERE m.brand_id = #{brandId}
              AND m.deleted = 0
              AND (
                    m.register_store_id IN
                    <foreach collection="storeIds" item="sid" open="(" separator="," close=")">#{sid}</foreach>
                    OR EXISTS (
                        SELECT 1 FROM oms_order o
                        WHERE o.member_id = m.id
                          AND o.deleted = 0
                          AND o.store_id IN
                          <foreach collection="storeIds" item="sid2" open="(" separator="," close=")">#{sid2}</foreach>
                    )
                  )
            </script>
            """)
    List<Long> selectVisibleMemberIds(@Param("brandId") Long brandId,
                                      @Param("storeIds") Collection<Long> storeIds);
}
