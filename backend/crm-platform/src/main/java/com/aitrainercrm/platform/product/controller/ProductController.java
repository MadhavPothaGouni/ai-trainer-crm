package com.aitrainercrm.platform.product.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.product.dto.CreateProductRequest;
import com.aitrainercrm.platform.product.dto.ProductDto;
import com.aitrainercrm.platform.product.dto.UpdateProductRequest;
import com.aitrainercrm.platform.product.entity.Product;
import com.aitrainercrm.platform.product.service.ProductService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** No OWN scope on PRODUCT (see ProductService's javadoc) - every @PreAuthorize here only lists TEAM/DEPARTMENT/ORGANIZATION. */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PRODUCT:READ:TEAM','PRODUCT:READ:DEPARTMENT','PRODUCT:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<ProductDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Product> page = productService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(ProductDto::from).toList()));
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAnyAuthority('PRODUCT:READ:TEAM','PRODUCT:READ:DEPARTMENT','PRODUCT:READ:ORGANIZATION')")
    public ApiResponse<ProductDto> get(@PathVariable UUID productId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ProductDto.from(productService.get(principal, productId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('PRODUCT:CREATE:TEAM','PRODUCT:CREATE:DEPARTMENT','PRODUCT:CREATE:ORGANIZATION')")
    public ApiResponse<ProductDto> create(@Valid @RequestBody CreateProductRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ProductDto.from(productService.create(principal, request)), "Product created");
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasAnyAuthority('PRODUCT:UPDATE:TEAM','PRODUCT:UPDATE:DEPARTMENT','PRODUCT:UPDATE:ORGANIZATION')")
    public ApiResponse<ProductDto> update(
            @PathVariable UUID productId, @Valid @RequestBody UpdateProductRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ProductDto.from(productService.update(principal, productId, request)), "Product updated");
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasAnyAuthority('PRODUCT:DELETE:TEAM','PRODUCT:DELETE:DEPARTMENT','PRODUCT:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID productId, @AuthenticationPrincipal UserPrincipal principal) {
        productService.delete(principal, productId);
        return ApiResponse.ok(null, "Product deleted");
    }
}
