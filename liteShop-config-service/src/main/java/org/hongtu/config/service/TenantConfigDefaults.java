package org.hongtu.config.service;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.Resource;
import org.hongtu.config.DAO.AppConfigMapper;
import org.hongtu.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service
public class TenantConfigDefaults {
    public record Entry(String key,String value,int group,int type,String name) {}
    public static final List<Entry> ENTRIES = List.of(
        new Entry("app_name","LiteShop",2,2,"应用名称"), new Entry("app_logo","",2,1,"应用图标"), new Entry("app_desc","",2,2,"应用描述"),
        new Entry("home_background_image","",1,1,"首页背景"), new Entry("order_background_image","",1,1,"点餐背景"),
        new Entry("default_dish_image","",1,1,"默认菜品图片"), new Entry("default_avatar","",1,1,"默认头像"),
        new Entry("default_empty_image","",1,1,"默认空状态图片"),new Entry("default_order_image","",1,1,"默认订单图片"),
        new Entry("theme_color","#FF6B35",3,4,"主题颜色"),new Entry("theme_color_light","#FFE4D4",3,4,"主题浅色"),new Entry("theme_color_dark","#E55A2B",3,4,"主题深色"),
        new Entry("service_phone","",4,2,"客服电话"),new Entry("service_wechat","",4,2,"客服微信"),new Entry("company_address","",4,2,"商户地址"),
        new Entry("announcement","",5,2,"公告内容"),new Entry("announcement_enabled","1",5,5,"启用公告"),
        new Entry("share_title","",5,2,"分享标题"),new Entry("share_image","",5,1,"分享图片"),
        new Entry("h5_base_url","",6,2,"H5 地址"),new Entry("miniapp_base_url","",6,2,"小程序地址"),new Entry("miniapp_appid","",6,2,"小程序 AppID"),
        new Entry("qrcode_default_platform","h5",6,2,"默认二维码平台")
    );
    @Resource private AppConfigMapper mapper;
    @Transactional(rollbackFor=Exception.class)
    public void ensureDefaults(String appName) { for(var entry:ENTRIES) insert(entry,appName); }
    public void ensureKey(String key) { ENTRIES.stream().filter(e->e.key().equals(key)).findFirst().ifPresent(e->insert(e,null)); }
    private void insert(Entry entry,String appName) {
        var value="app_name".equals(entry.key()) && appName!=null && !appName.isBlank()?appName:entry.value();
        mapper.insertDefault(IdWorker.getId(),TenantContext.getTenantId(),entry.key(),value,entry.group(),entry.type(),entry.name());
    }
}
