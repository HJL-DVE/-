package com.teaclub.product.controller;

import com.teaclub.common.annotation.AuditLog;
import com.teaclub.common.api.PageResult;
import com.teaclub.common.api.Result;
import com.teaclub.product.dto.StoreShelfRequest;
import com.teaclub.product.dto.StoreSkuPriceRequest;
import com.teaclub.product.service.PrdStoreProductService;
import com.teaclub.product.vo.StoreSpuVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "门店商品管理", description = "门店选品、上下架与差异化定价（受门店数据权限约束）")
@RestController
@RequestMapping("/api/admin/product/store-products")
@RequiredArgsConstructor
public class PrdStoreProductController {

    private final PrdStoreProductService storeProductService;

    @Operation(summary = "门店商品分页（storeId 必填，后端校验门店权限）")
    @GetMapping
    @PreAuthorize("hasAuthority('product:store:list')")
    public Result<PageResult<StoreSpuVO>> page(@RequestParam Long storeId,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Integer pageNo,
                                               @RequestParam(required = false) Integer pageSize) {
        return Result.ok(storeProductService.page(storeId, keyword, pageNo, pageSize));
    }

    @Operation(summary = "门店商品详情")
    @GetMapping("/{storeId}/spu/{spuId}")
    @PreAuthorize("hasAuthority('product:store:list')")
    public Result<StoreSpuVO> detail(@PathVariable Long storeId, @PathVariable Long spuId) {
        return Result.ok(storeProductService.detail(storeId, spuId));
    }

    @Operation(summary = "上架/下架门店商品")
    @PatchMapping("/{storeId}/spu/{spuId}/shelf")
    @PreAuthorize("hasAuthority('product:store:update')")
    @AuditLog(module = "门店商品", value = "门店商品上下架")
    public Result<Void> shelf(@PathVariable Long storeId, @PathVariable Long spuId,
                              @Valid @RequestBody StoreShelfRequest request) {
        storeProductService.shelf(storeId, spuId, request);
        return Result.ok();
    }

    @Operation(summary = "批量上架/下架")
    @PatchMapping("/{storeId}/batch-shelf")
    @PreAuthorize("hasAuthority('product:store:update')")
    @AuditLog(module = "门店商品", value = "批量上下架门店商品")
    public Result<Void> batchShelf(@PathVariable Long storeId,
                                   @RequestParam List<Long> spuIds,
                                   @RequestParam Integer isOnSale) {
        storeProductService.batchShelf(storeId, spuIds, isOnSale);
        return Result.ok();
    }

    @Operation(summary = "设置门店 SKU 售价（校验最低保护价）")
    @PutMapping("/{storeId}/sku/{skuId}/price")
    @PreAuthorize("hasAuthority('product:store:update')")
    @AuditLog(module = "门店商品", value = "设置门店差异化定价")
    public Result<Void> setPrice(@PathVariable Long storeId, @PathVariable Long skuId,
                                 @Valid @RequestBody StoreSkuPriceRequest request) {
        storeProductService.setSkuPrice(storeId, skuId, request);
        return Result.ok();
    }

    @Operation(summary = "移除门店商品")
    @DeleteMapping("/{storeId}/spu/{spuId}")
    @PreAuthorize("hasAuthority('product:store:update')")
    @AuditLog(module = "门店商品", value = "移除门店商品")
    public Result<Void> remove(@PathVariable Long storeId, @PathVariable Long spuId) {
        storeProductService.remove(storeId, spuId);
        return Result.ok();
    }
}
