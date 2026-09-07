package org.hongtu.utils;



import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.File;

/**
 * 发送邮件工具类 MailUtil
 *
 * @author heshi
 * @date 2021/3/22 16:52
 */
 
@Service
public class EmailUtil
{
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
 
    //Spring Boot 提供了一个发送邮件的简单抽象，使用的是下面这个接口，这里直接注入即可使用
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Resource
    private TemplateEngine templateEngine;

    // 网站名称
    @Value("${app.webSite.name:LiteShop}")
    private String webSiteName;
 
    // 配置文件中我的qq邮箱
    @Value("${spring.mail.from:}")
    private String from;

    public boolean isConfigured() {
        return mailSender != null && from != null && !from.isBlank();
    }
 
    /**
     * 简单文本邮件
     * @param to 收件人
     * @param subject 主题
     * @param content 内容
     */
    public void sendSimpleMail(String to, String subject, String content) {
        //创建SimpleMailMessage对象
        SimpleMailMessage message = new SimpleMailMessage();
        //邮件发送人
        message.setFrom(from);
        //邮件接收人
        message.setTo(to);
        //邮件主题
        message.setSubject(subject);
        //邮件内容
        message.setText(content);
        //发送邮件
        mailSender.send(message);
    }
 
    /**
     * html邮件
     * @param to 收件人,多个时参数形式 ："xxx@xxx.com,xxx@xxx.com,xxx@xxx.com"
     * @param subject 主题
     * @param content 内容
     */
    public void sendHtmlMail(String to, String subject, String content) {
        if (!isConfigured()) throw new IllegalStateException("邮件服务未配置");
        //获取MimeMessage对象
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper messageHelper;
        try {
            messageHelper = new MimeMessageHelper(message, true);
            //邮件发送人
            messageHelper.setFrom(from);
            //邮件接收人,设置多个收件人地址
            InternetAddress[] internetAddressTo = InternetAddress.parse(to);
            messageHelper.setTo(internetAddressTo);
            //messageHelper.setTo(to);
            //邮件主题
            message.setSubject(subject);
            //邮件内容，html格式
            messageHelper.setText(content, true);
            //发送
            mailSender.send(message);
            //日志信息
            logger.info("邮件已经发送。");
        } catch (Exception e) {
            logger.error("发送邮件时发生异常！", e);
            throw new IllegalStateException("邮件发送失败", e);
        }
    }
 
    /**
     * 带附件的邮件
     * @param to 收件人
     * @param subject 主题
     * @param content 内容
     * @param filePath 附件
     */
    public void sendAttachmentsMail(String to, String subject, String content, String filePath) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);
 
            FileSystemResource file = new FileSystemResource(new File(filePath));
            String fileName = filePath.substring(filePath.lastIndexOf(File.separator));
            helper.addAttachment(fileName, file);
            mailSender.send(message);
            //日志信息
            logger.info("邮件已经发送。");
        } catch (Exception e) {
            logger.error("发送邮件时发生异常！", e);
        }
    }


    /**
     * 获取HTML邮件内容
     *
     * @param code 验证码
     * @return HTML内容
     */
    public String getHtmlEmailContent(String code) {
        try {
            // 创建Thymeleaf上下文
            Context context = new Context();
            // 设置变量
            // 将验证码拆分为字符数组
            char[] digits = code.toCharArray();
            context.setVariable("digits", digits);
            context.setVariable("code", code);
            context.setVariable("websiteName", webSiteName);
            // 处理模板并返回处理后的HTML内容
            return templateEngine.process("emailCode", context);
        } catch (Exception e) {
            // 如果模板处理失败，返回简单的HTML内容
            StringBuilder simpleCodeHtml = new StringBuilder("<div style='display:flex;justify-content:center;gap:10px;margin:20px 0;'>");
            for (char digit : code.toCharArray()) {
                simpleCodeHtml.append("<div style='width:40px;height:50px;background:#f0f4ff;border-radius:8px;display:flex;align-items:center;justify-content:center;font-size:24px;font-weight:bold;color:#3498db;box-shadow:0 2px 5px rgba(0,0,0,0.1);'>")
                        .append(digit)
                        .append("</div>");
            }
            simpleCodeHtml.append("</div>");

            return "<div style='text-align:center;'>" +
                    "<h2 style='color:#3498db;'>" + webSiteName + "</h2>" +
                    "<p>您的验证码是：</p>" +
                    simpleCodeHtml.toString() +
                    "<p>验证码有效期为5分钟，请勿泄露给他人。</p>" +
                    "</div>";
        }
    }
}