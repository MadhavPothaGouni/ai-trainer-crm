package com.aitrainercrm.platform.notification.email;

/**
 * Transactional email abstraction. The full templated/queued/tracked email
 * subsystem (item #20 in the platform roadmap - templates, bounce
 * handling, open/click tracking, suppression lists) lands in a later
 * phase; this interface is what every module that needs to send an email
 * (auth's verification/reset links today, campaigns/notifications later)
 * codes against, so swapping the LoggingEmailService stub below for a
 * real SMTP/SES-backed implementation never touches a caller.
 */
public interface EmailService {

    void sendPasswordResetEmail(String toEmail, String resetToken);

    void sendEmailVerificationEmail(String toEmail, String verificationToken);

    void sendWelcomeEmail(String toEmail, String firstName);
}
