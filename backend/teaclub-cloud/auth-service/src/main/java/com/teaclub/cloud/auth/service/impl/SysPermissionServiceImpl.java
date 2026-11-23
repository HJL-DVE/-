package com.teaclub.cloud.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.teaclub.cloud.auth.entity.SysPermission;
import com.teaclub.cloud.auth.mapper.SysPermissionMapper;
import com.teaclub.cloud.auth.service.SysPermissionService;
import com.teaclub.cloud.auth.vo.PermissionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SysPermissionServiceImpl implements SysPermissionService {

    private final SysPermissionMapper permissionMapper;

    @Override
    public List<PermissionVO> tree() {
        List<SysPermission> all = permissionMapper.selectList(Wrappers.<SysPermission>lambdaQuery()
                .orderByAsc(SysPermission::getSort)
                .orderByAsc(SysPermission::getId));
        return buildTree(all);
    }

    private List<PermissionVO> buildTree(List<SysPermission> flat) {
        Map<Long, PermissionVO> map = new LinkedHashMap<>();
        for (SysPermission p : flat) {
            PermissionVO vo = new PermissionVO();
            vo.setId(p.getId());
            vo.setParentId(p.getParentId());
            vo.setPermCode(p.getPermCode());
            vo.setPermName(p.getPermName());
            vo.setPermType(p.getPermType());
            vo.setRoutePath(p.getRoutePath());
            vo.setComponent(p.getComponent());
            vo.setIcon(p.getIcon());
            vo.setSort(p.getSort());
            vo.setVisible(p.getVisible());
            vo.setStatus(p.getStatus());
            map.put(p.getId(), vo);
        }
        List<PermissionVO> roots = new ArrayList<>();
        for (PermissionVO vo : map.values()) {
            Long pid = vo.getParentId();
            if (pid == null || pid == 0L || !map.containsKey(pid)) {
                roots.add(vo);
            } else {
                map.get(pid).getChildren().add(vo);
            }
        }
        return roots;
    }
}
