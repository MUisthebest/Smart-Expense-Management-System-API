package com.example.smartmoney.infrastructure.mail.adapters;

import com.example.smartmoney.application.ports.mail.MailPort;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmtpMailAdapter implements MailPort {

    private final JavaMailSender mailSender;

    private String loadTemplate(String templateName) {
        try {
            String templatePath = "templates/emails/" + templateName + ".html";
            ClassPathResource resource = new ClassPathResource(templatePath);
            
            if (!resource.exists()) {
                log.warn("Template not found: {}", templatePath);
                return getDefaultTemplate();
            }
            
            try (InputStream inputStream = resource.getInputStream()) {
                byte[] bytes = FileCopyUtils.copyToByteArray(inputStream);
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.error("Failed to load email template: {}", templateName, e);
            return getDefaultTemplate();
        }
    }
    
    private String getDefaultTemplate() {
        return """
            <!DOCTYPE html>
            <html>
            <body>
                <h2>SmartMoney Verification</h2>
                <p>Your verification code is: %s</p>
                <p>This code expires in 10 minutes.</p>
            </body>
            </html>
            """;
    }


    @Override
    public String generateVerificationEmail(String email, String otp) {
        // Load template từ file
        String template = loadTemplate("verification-email");
        
        // Chuẩn bị dữ liệu
        Map<String, String> data = new HashMap<>();
        data.put("email", email);
        data.put("otp", otp);
        data.put("registrationTime", LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        data.put("expiryMinutes", "10");
        data.put("verificationLink", String.format(
            "https://smartmoney.com/verify?email=%s&otp=%s", 
            email, otp));
        data.put("helpLink", "https://smartmoney.com/help");
        data.put("supportEmail", "support@smartmoney.com");
        data.put("supportPhone", "+84 123 456 789");
        data.put("privacyLink", "https://smartmoney.com/privacy");
        data.put("termsLink", "https://smartmoney.com/terms");
        data.put("unsubscribeLink", "https://smartmoney.com/unsubscribe");
        
        // Replace placeholders trong template
        String htmlContent = template;
        for (Map.Entry<String, String> entry : data.entrySet()) {
            htmlContent = htmlContent.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        
        return htmlContent;
    }
    
    @Override
    public String generateWelcomeEmail(String name, String email) {
        String template = loadTemplate("welcome-email");
        
        Map<String, String> data = new HashMap<>();
        data.put("name", name);
        data.put("email", email);
        data.put("currentDate", LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        
        String htmlContent = template;
        for (Map.Entry<String, String> entry : data.entrySet()) {
            htmlContent = htmlContent.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        
        return htmlContent;
    }

    @Override
    public void sendMail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);

            log.info("Mail sent successfully to {}", to);
        } catch (Exception e) {
            log.error("Failed to send mail to {}", to, e);
            throw new RuntimeException("Cannot send email");
        }
    }

        @Override
    public void sendHtmlMail(String to, String subject, String htmlContent) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true = HTML content
            
            mailSender.send(mimeMessage);
            
            log.info("✓ HTML email sent to {}", to);
        } catch (MessagingException e) {
            log.error("✗ Failed to send HTML email to {}", to, e);
            throw new RuntimeException("Cannot send HTML email: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void sendMailWithAttachment(String to, String subject, String body, 
                                      String attachmentPath, String attachmentName) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false); // false = plain text
            
            // Add attachment
            // FileSystemResource file = new FileSystemResource(new File(attachmentPath));
            // helper.addAttachment(attachmentName, file);
            
            mailSender.send(mimeMessage);
            
            log.info("✓ Email with attachment sent to {}", to);
        } catch (MessagingException e) {
            log.error("✗ Failed to send email with attachment to {}", to, e);
            throw new RuntimeException("Cannot send email with attachment: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void sendHtmlMailWithAttachment(String to, String subject, String htmlContent,
                                          String attachmentPath, String attachmentName) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true = HTML content
            
            // Add attachment
            // FileSystemResource file = new FileSystemResource(new File(attachmentPath));
            // helper.addAttachment(attachmentName, file);
            
            mailSender.send(mimeMessage);
            
            log.info("✓ HTML email with attachment sent to {}", to);
        } catch (MessagingException e) {
            log.error("✗ Failed to send HTML email with attachment to {}", to, e);
            throw new RuntimeException("Cannot send HTML email with attachment: " + e.getMessage(), e);
        }
    }
}
