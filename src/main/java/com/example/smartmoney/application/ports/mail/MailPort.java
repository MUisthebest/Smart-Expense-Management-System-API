package com.example.smartmoney.application.ports.mail;

public interface MailPort {
    String generateVerificationEmail(String email, String otp);
    String generateWelcomeEmail(String name, String email);
    void sendMail(String to, String subject, String body);    
    void sendHtmlMail(String to, String subject, String htmlContent);
    
    void sendMailWithAttachment(String to, String subject, String body, 
                               String attachmentPath, String attachmentName);
    
    void sendHtmlMailWithAttachment(String to, String subject, String htmlContent,
                                   String attachmentPath, String attachmentName);
}
