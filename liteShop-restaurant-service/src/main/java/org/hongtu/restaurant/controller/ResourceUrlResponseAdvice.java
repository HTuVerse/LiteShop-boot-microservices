package org.hongtu.restaurant.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.hongtu.Result.Result;
import org.hongtu.api.util.ResourceBindingCoordinator;
import org.hongtu.restaurant.VO.cart.CartItemVO;
import org.hongtu.restaurant.DTO.Dish;
import org.hongtu.restaurant.DTO.DishCategory;
import org.hongtu.restaurant.DTO.DishSpecOption;
import org.hongtu.restaurant.DTO.PrintTemplate;
import org.hongtu.restaurant.DTO.StoreInfo;
import org.hongtu.restaurant.VO.DishByCategoryVO;
import org.hongtu.restaurant.VO.DishVO;
import org.hongtu.restaurant.utils.SpecValueConverter;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Map;

/** 保持旧响应字段，同时根据资源 ID 输出当前公开 URL，兼容后台与小程序。 */
@ControllerAdvice(basePackages = "org.hongtu.restaurant.controller")
@RequiredArgsConstructor
public class ResourceUrlResponseAdvice implements ResponseBodyAdvice<Object> {
    private final ResourceBindingCoordinator links;

    @Override
    public boolean supports(MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter method, MediaType mediaType,
                                  Class<? extends HttpMessageConverter<?>> converter,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Result<?> result) hydrate(result.getData());
        return body;
    }

    private void hydrate(Object value) {
        if (value == null) return;
        if (value instanceof IPage<?> page) { page.getRecords().forEach(this::hydrate); return; }
        if (value instanceof Iterable<?> items) { items.forEach(this::hydrate); return; }
        if (value instanceof Map<?, ?> map) { map.values().forEach(this::hydrate); return; }
        if (value instanceof DishByCategoryVO group) {
            if (group.getCategoryId() != null)
                group.setCategoryIcon(links.currentSingle("CATEGORY", group.getCategoryId(),
                        "categoryIcon", group.getCategoryIcon()));
            hydrate(group.getDishes()); return;
        }
        if (value instanceof DishVO dish) {
            String legacy = dish.getDishPics();
            dish.setDishPics(links.currentMulti("DISH", dish.getId(), "dishPics", legacy));
            dish.setDishPicResourceIds(links.currentMultiIds("DISH", dish.getId(), "dishPics", legacy));
            if (dish.getDishPics() != null && !dish.getDishPics().isBlank())
                dish.setMainPic(links.firstUrl(dish.getDishPics()));
            hydrate(dish.getSpecOptions());
        } else if (value instanceof Dish dish) {
            String legacy = dish.getDishPics();
            dish.setDishPics(links.currentMulti("DISH", dish.getId(), "dishPics", legacy));
            dish.setDishPicResourceIds(links.currentMultiIds("DISH", dish.getId(), "dishPics", legacy));
        } else if (value instanceof DishCategory category) {
            category.setCategoryPic(links.currentSingle("CATEGORY", category.getId(), "categoryPic", category.getCategoryPic()));
            category.setCategoryImageResourceId(links.currentSingleId("CATEGORY", category.getId(), "categoryPic"));
            category.setCategoryIcon(links.currentSingle("CATEGORY", category.getId(), "categoryIcon", category.getCategoryIcon()));
        } else if (value instanceof StoreInfo store) {
            String legacy = store.getStoreImages();
            store.setStoreImages(links.currentMulti("STORE", store.getId(), "storeImages", legacy));
            store.setStoreImageResourceIds(links.currentMultiIds("STORE", store.getId(), "storeImages", legacy));
        } else if (value instanceof PrintTemplate template) {
            template.setLogoUrl(links.currentSingle("TEMPLATE", template.getId(), "logoUrl", template.getLogoUrl()));
            template.setLogoResourceId(links.currentSingleId("TEMPLATE", template.getId(), "logoUrl"));
        } else if (value instanceof CartItemVO cart) {
            cart.setDishPics(links.currentMulti("DISH", cart.getDishId(), "dishPics", cart.getDishPics()));
        } else if (value instanceof DishSpecOption option) {
            var values = option.getSpecValueList();
            for (var slot : links.fieldReferences("SPEC", option.getId(), "specValues")) {
                if (slot.sortOrder() < values.size()) values.get(slot.sortOrder()).setImageUrl(slot.url());
            }
            option.setSpecValues(SpecValueConverter.convertToString(values));
        }
    }
}
