package org.hongtu.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.symmetric.AES;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import org.apache.commons.codec.binary.Base64;
import org.hongtu.config.WeChatProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * 功能：
 * 作者：
 * 时间：2025/5/13 下午4:52
 */
@Component
public class wxUtils {

    @Resource
    WeChatProperties weChatProperties;
    @Resource
    RedisCache redisCache;

    //微信登入服务接口地址
    public static final String WX_LOGIN = "https://api.weixin.qq.com/sns/jscode2session";
    //微信获取全局凭证接口地址
    public static final String WX_getAccessToken =  "https://api.weixin.qq.com/cgi-bin/token";
    //微信获取手机号登入接口地址
    public static final String WX_getPhoneNumber = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=";
    /**
     * 解密微信加密数据，对称解密使用的算法为 AES-128-CBC，数据采用PKCS#7填充。
     *
     * @param encryptedData 加密串
     * @param sessionKey    会话密钥
     * @param iv            解密算法初始向量
     * @return 解密后的数据
     */
    public String decryptWxDataOfHutool(String encryptedData, String sessionKey, String iv) {
        AES aes = new AES(Mode.CBC, Padding.NoPadding, Base64.decodeBase64(sessionKey),
                Base64.decodeBase64(iv));
        byte[] resultByte = aes.decrypt(Base64.decodeBase64(encryptedData));
        if (null != resultByte && resultByte.length > 0) {
            // 删除解密后明文的补位字符
            int padNum = resultByte[resultByte.length - 1];
            if (padNum < 1 || padNum > 32) {
                padNum = 0;
            }
            resultByte = Arrays.copyOfRange(resultByte, 0, resultByte.length - padNum);
            String result = new String(resultByte, StandardCharsets.UTF_8);
            return result;
        }
        return null;
    }

    /**
     * 前端返回code进行登入获取openid和sessionKey
     * @param code
     * */

    public String wxLogin(String code) {
        HashMap<String, String> map = new HashMap<>();
        //封装参数
        map.put("appid", weChatProperties.getAppid());
        map.put("secret",weChatProperties.getSecret());
        map.put("js_code",code);
        map.put("grant_type","authorization_code");
        try {
            String json = HttpClientUtil.doGet(WX_LOGIN, map);
            return json;
        }catch (Exception e){
           e.printStackTrace();
           return null;
        }
    }

    /**
     * 获取接口调用凭据
     *
     * @return
     * access_token 获取到的凭证
     * expires_in   过期时间
     * */

    public String wxGetAccessToken() {
        HashMap<String, String> map = new HashMap<>();
        //封装参数
        map.put("appid", weChatProperties.getAppid());
        map.put("secret",weChatProperties.getSecret());
        map.put("grant_type","client_credential");
        try {
            String json = HttpClientUtil.doGet(WX_getAccessToken, map);
            String accessToken = JSON.parseObject(json).getString("access_token");
            // access_token存放在redis中，并设置过期时间
            if(!StrUtil.isEmpty(accessToken)){
                redisCache.setCacheObject("access_token",accessToken, 2,TimeUnit.HOURS);
                return accessToken;
            }else {
                throw new RuntimeException("微信接口调用失败: accesstoken为空 " );
            }
        }catch (Exception e){
            throw new RuntimeException("微信接口调用失败: " + e.getMessage());
        }
    }

    /**
     * 获取接口调用凭据
     *
     * @param access_token
     * @param code  //手机号凭证跟login不同
     * */

    public String wxGetPhoneNumber(String access_token,String code) {
        HashMap<String, String> map = new HashMap<>();
        //封装参数
        String url = WX_getPhoneNumber+access_token;
        map.put("code",code);
        try {
            String json = HttpClientUtil.doPostForJson(url, map);
            JSONObject root = JSON.parseObject(json);
            if( root.getString("errmsg").equals("ok")){
               return root.getJSONObject("phone_info").getString("phoneNumber");
            }else {
                throw new RuntimeException("微信接口调用失败: " + root.getString("errmsg"));
            }
        }catch (Exception e){
            throw new RuntimeException("微信接口调用失败: " + e.getMessage());
        }
    }


}
