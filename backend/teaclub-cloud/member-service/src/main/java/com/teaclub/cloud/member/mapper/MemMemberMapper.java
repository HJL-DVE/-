package com.teaclub.cloud.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teaclub.cloud.member.entity.MemMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface MemMemberMapper extends BaseMapper<MemMember> {

    /**
     * 查询注册门店在指定门店范围内的会员 ID。
     *
     * <p>可见口径（B 方案）的一半：注册门店归属。
     * 另一半"在授权门店消费过"由 order-service 提供（跨服务调用）。
     *
     * @param brandId  品牌 ID
     * @param storeIds 授权门店 ID 集合（非空）
     */
    @Select("""
            <script>
            SELECT m.id
            FROM mem_member m
            WHERE m.brand_id = #{brandId}
              AND m.deleted = 0
              AND m.register_store_id IN
              <foreach collection="storeIds" item="sid" open="(" separator="," close=")">#{sid}</foreach>
            </script>
            """)
    List<Long> selectIdsByRegisterStores(@Param("brandId") Long brandId,
                                        @Param("storeIds") Collection<Long> storeIds);
}
