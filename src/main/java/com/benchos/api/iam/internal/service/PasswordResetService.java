package com.benchos.api.iam.internal.service;

import com.benchos.api.iam.internal.entity.PasswordResetToken;
import com.benchos.api.iam.internal.entity.Tenant;
import com.benchos.api.iam.internal.entity.User;
import com.benchos.api.iam.internal.repository.PasswordResetTokenRepository;
import com.benchos.api.iam.internal.repository.TenantRepository;
import com.benchos.api.iam.internal.repository.UserRepository;
import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.mail.EmailMessage;
import com.benchos.api.shared.mail.EmailSender;
import com.benchos.api.shared.mail.MailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final String INVALID_CODE_MESSAGE = "Invalid or expired verification code";

    private final SecureRandom secureRandom = new SecureRandom();
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final MailProperties mailProperties;

    public PasswordResetService(
            UserRepository userRepository,
            TenantRepository tenantRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            MailProperties mailProperties
    ) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.mailProperties = mailProperties;
    }

    @Transactional
    public void requestPasswordReset(String rawEmail) {
        String normalizedEmail = User.normalizeEmail(rawEmail);
        Optional<User> maybeUser = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (maybeUser.isEmpty()) {
            return;
        }

        User user = maybeUser.get();
        if (!user.isActive()) {
            return;
        }

        Tenant tenant = tenantRepository.findById(user.getTenantId())
                .filter(Tenant::isActive)
                .orElse(null);
        if (tenant == null) {
            return;
        }

        Instant now = Instant.now();
        Duration cooldown = mailProperties.resendCooldown();
        if (!cooldown.isZero()) {
            Optional<PasswordResetToken> latestActive = tokenRepository
                    .findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId());
            if (latestActive.isPresent()
                    && !latestActive.get().isExpired(now)
                    && latestActive.get().getCreatedAt().plus(cooldown).isAfter(now)) {
                log.debug("Skipping duplicate password reset email within cooldown for userId={}", user.getId());
                return;
            }
        }

        tokenRepository.invalidateAllActiveForUser(user.getId(), now);

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        String codeHash = sha256Hex(code);
        Duration ttl = mailProperties.resetCodeTtl();
        PasswordResetToken token = tokenRepository.save(
                new PasswordResetToken(user.getId(), codeHash, now.plus(ttl), now)
        );

        long expiresMinutes = Math.max(1, ttl.toMinutes());
        EmailMessage emailMessage = buildResetEmail(user, tenant, code, expiresMinutes);
        try {
            emailSender.send(emailMessage);
        } catch (RuntimeException ex) {
            // Do not leave an undelivered token blocking retries and avoid leaking account existence
            token.markUsed(now);
            tokenRepository.save(token);
            log.error("Unable to dispatch password reset email for userId={}", user.getId(), ex);
        }
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public void resetPassword(String rawEmail, String rawCode, String newPassword) {
        String normalizedEmail = User.normalizeEmail(rawEmail);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessRuleException(INVALID_CODE_MESSAGE));

        boolean tenantActive = tenantRepository.findById(user.getTenantId())
                .map(Tenant::isActive)
                .orElse(false);
        if (!tenantActive) {
            throw new BusinessRuleException(INVALID_CODE_MESSAGE);
        }

        PasswordResetToken token = tokenRepository
                .findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new BusinessRuleException(INVALID_CODE_MESSAGE));

        Instant now = Instant.now();
        if (token.isExpired(now) || token.hasExceededMaxAttempts()) {
            token.markUsed(now);
            tokenRepository.save(token);
            throw new BusinessRuleException(INVALID_CODE_MESSAGE);
        }

        String candidateHash = sha256Hex(rawCode.trim());
        boolean matches = MessageDigest.isEqual(
                token.getCodeHash().getBytes(StandardCharsets.UTF_8),
                candidateHash.getBytes(StandardCharsets.UTF_8)
        );
        if (!matches) {
            token.incrementAttempts();
            if (token.hasExceededMaxAttempts()) {
                token.markUsed(now);
            }
            tokenRepository.save(token);
            throw new BusinessRuleException(INVALID_CODE_MESSAGE);
        }

        token.markUsed(now);
        tokenRepository.save(token);

        user.updatePasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private EmailMessage buildResetEmail(User user, Tenant tenant, String code, long expiresMinutes) {
        String subject = "BenchOS — Código de recuperação de senha: " + code;
        String safeName = escapeHtml(user.getName());
        String safeShop = escapeHtml(tenant.getName());

        String textBody = """
                Olá, %s!

                Recebemos uma solicitação para redefinir a senha do seu acesso no BenchOS (%s).

                Seu código de verificação é: %s

                Este código expira em %d minutos e só pode ser utilizado uma vez.
                Se você não solicitou esta alteração, basta ignorar este e-mail.
                """.formatted(user.getName(), tenant.getName(), code, expiresMinutes);

        String htmlBody = """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <body style="margin:0;padding:24px;background-color:#F8FAFC;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;color:#0F172A;">
                  <div style="max-width:480px;margin:0 auto;background:#FFFFFF;border:1px solid #E2E8F0;border-radius:12px;overflow:hidden;">
                    <div style="background:#2563EB;padding:20px 24px;">
                      <div style="font-size:18px;font-weight:700;color:#FFFFFF;letter-spacing:-0.3px;">BenchOS</div>
                      <div style="font-size:11px;font-weight:600;color:#DBEAFE;letter-spacing:0.8px;margin-top:2px;">RECUPERAÇÃO DE SENHA</div>
                    </div>
                    <div style="padding:24px;">
                      <p style="margin:0 0 12px;font-size:15px;">Olá, <strong>%s</strong>!</p>
                      <p style="margin:0 0 20px;font-size:14px;line-height:1.5;color:#475569;">
                        Recebemos uma solicitação para redefinir a senha da sua conta em <strong>%s</strong>.
                        Digite o código abaixo no aplicativo BenchOS:
                      </p>
                      <div style="background:#EFF6FF;border:1px solid #BFDBFE;border-radius:10px;padding:18px;text-align:center;margin:0 0 20px;">
                        <div style="font-family:'JetBrains Mono','Courier New',monospace;font-size:32px;font-weight:700;letter-spacing:8px;color:#1D4ED8;">%s</div>
                      </div>
                      <p style="margin:0 0 8px;font-size:13px;color:#475569;">
                        Este código é válido por <strong>%d minutos</strong> e pode ser usado apenas uma vez.
                      </p>
                      <p style="margin:0;font-size:12px;color:#64748B;">
                        Se você não solicitou a redefinição de senha, ignore este e-mail — sua conta continua protegida.
                      </p>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(safeName, safeShop, code, expiresMinutes);

        return new EmailMessage(user.getEmail(), subject, htmlBody, textBody);
    }

    private static String escapeHtml(String raw) {
        return raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
