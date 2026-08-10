package com.aitrainercrm.platform.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default EmailService: logs what would have been sent instead of actually
 * sending it. Exactly the same reasoning as the mock LLM provider in the
 * AI-Trainer project - every auth flow (registration, password reset)
 * needs to be fully exercisable and testable without a real SMTP/SES
 * account configured. Swap in a real implementation (see
 * notification/email/ses or /smtp once those exist) via a Spring profile
 * when this goes to production.
 */
@Service
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        log.info("[email:stub] Password reset link for {}: /reset-password?token={}", toEmail, resetToken);
    }

    @Override
    public void sendEmailVerificationEmail(String toEmail, String verificationToken) {
        log.info("[email:stub] Email verification link for {}: /verify-email?token={}", toEmail, verificationToken);
    }

    @Override
    public void sendWelcomeEmail(String toEmail, String firstName) {
        log.info("[email:stub] Welcome email queued for {} ({})", toEmail, firstName);
    }
}
