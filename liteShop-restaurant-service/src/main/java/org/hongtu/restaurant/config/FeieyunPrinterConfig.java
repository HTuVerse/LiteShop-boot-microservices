package org.hongtu.restaurant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 飞鹅打印机配置类
 * 
 * @author hongtu
 */
@Data
@Component
@ConfigurationProperties(prefix = "feieyun.printer")
public class FeieyunPrinterConfig {
    
    /**
     * 飞鹅云API接口地址
     */
    private String apiUrl = "http://api.feieyun.cn/Api/Open/";
    
    /**
     * 飞鹅云账号
     */
    private String user;
    
    /**
     * 飞鹅云后台注册账号后生成的UKEY（注意：不是打印机的KEY）
     */
    private String ukey;
    
    /**
     * 打印机编号（可选，用于单打印机场景）
     */
    private String sn;


    /**
     * 飞鹅云公钥（用于回调签名验证）
     */
    private String publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA2LDlvyuClqrnKW01FqYg" +
            "valPy1/e09ZWlvjb5Nu+0T1PsGhKjF4WBb+D7x3Dy/Db5IHMcpG/Eps6ew6n/6rw" +
            "v8Ctu+uZI33YNv9sqAMPjG2EN+WcqCrVrMGUmjITVpIkQEjTdkuismf+VL3x+eJo" +
            "W1y/TaLb9vchReBc6IZowRu2yItC+tFbock5Nupsl5uOCKltm3s0VqtiHUrpgVeV" +
            "8dVJHLhmLENnLgcTqrkZeKogFDT+fTOhzQPZVEqQgdat/6kcmD44lN4UI7EvVNfe" +
            "amwRLgy4e/CpInD9cql+t5eiRLem0+rgPq9RLivM1pRt67crH0WGY1xXtAtzWO0M" +
            "MwIDAQAB";
}

