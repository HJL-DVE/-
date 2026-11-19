package com.teaclub.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "权限树节点")
public class PermissionVO {
    private Long id;
    private Long parentId;
    private String permCode;
    private String permName;
    private String permType;
    private String routePath;
    private String component;
    private String icon;
    private Integer sort;
    private Integer visible;
    private String status;
    private List<PermissionVO> children = new ArrayList<>();
}
