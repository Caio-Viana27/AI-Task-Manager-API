package br.com.planned.api.service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.planned.api.dto.AuthResponse;
import br.com.planned.api.dto.AuthUserResponse;
import br.com.planned.api.dto.SignInRequest;
import br.com.planned.api.dto.SignUpRequest;
import br.com.planned.api.dto.validation.MaxUtf8BytesValidator;
import br.com.planned.api.entity.Role;
import br.com.planned.api.entity.User;
import br.com.planned.api.exception.ApiException;
import br.com.planned.api.exception.ErrorCode;
import br.com.planned.api.repository.RoleRepository;
import br.com.planned.api.repository.UserRepository;

/** Sign-up and sign-in (PLAN §3). */
@Service
public class AuthService {

	/** BCrypt only reads this many bytes, and the encoder rejects longer input (wave 1, D5). */
	static final int MAX_PASSWORD_BYTES = 72;

	/** The two unique constraints on {@code USERS.EMAIL} (V1 and the case-insensitive index). */
	private static final Set<String> EMAIL_CONSTRAINTS = Set.of("users_email_key", "ux_users_email_lower");

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final Clock clock;

	/** Checked when there's no real hash, so response time doesn't reveal which emails exist. */
	private final String dummyHash;

	public AuthService(
			UserRepository userRepository,
			RoleRepository roleRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			Clock clock) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.clock = clock;
		// Same encoder, so the same cost factor as the real hashes.
		this.dummyHash = passwordEncoder.encode("dummy-password-for-unknown-emails");
	}

	@Transactional
	public AuthResponse signUp(SignUpRequest request) {
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw emailAlreadyUsed();
		}
		Role role = roleRepository.findByName(Role.USER)
				.orElseThrow(() -> new IllegalStateException("The USER role is not seeded"));
		User user = new User(
				request.name().strip(),
				email,
				passwordEncoder.encode(request.password()),
				role,
				clock.instant().truncatedTo(ChronoUnit.MICROS));
		try {
			// Flush now so a concurrent sign-up's unique violation surfaces here, not at commit.
			userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException ex) {
			if (violatesEmailConstraint(ex)) {
				throw emailAlreadyUsed();
			}
			throw ex;
		}
		return authResponse(user);
	}

	@Transactional(readOnly = true)
	public AuthResponse signIn(SignInRequest request) {
		Optional<User> user = userRepository.findByEmail(normalizeEmail(request.email()));
		boolean tooLong = MaxUtf8BytesValidator.utf8Length(request.password()) > MAX_PASSWORD_BYTES;
		if (user.isEmpty() || tooLong) {
			// Never pass an over-long password to the encoder, which would throw.
			passwordEncoder.matches("dummy-password", dummyHash);
			throw badCredentials();
		}
		if (!passwordEncoder.matches(request.password(), user.get().getPassword())) {
			throw badCredentials();
		}
		return authResponse(user.get());
	}

	/** Lowercase with {@link Locale#ROOT}, so no locale rule (e.g. Turkish dotless i) applies (wave 1, D4). */
	static String normalizeEmail(String email) {
		return email.toLowerCase(Locale.ROOT);
	}

	private AuthResponse authResponse(User user) {
		JwtService.IssuedToken token = jwtService.issue(user);
		return new AuthResponse(token.token(), token.expiresAt(), AuthUserResponse.from(user));
	}

	private static boolean violatesEmailConstraint(DataIntegrityViolationException ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				String name = violation.getConstraintName();
				return name != null && EMAIL_CONSTRAINTS.contains(name.toLowerCase(Locale.ROOT));
			}
		}
		return false;
	}

	private static ApiException emailAlreadyUsed() {
		return new ApiException(ErrorCode.EMAIL_ALREADY_USED, "This email is already registered");
	}

	private static ApiException badCredentials() {
		return new ApiException(ErrorCode.BAD_CREDENTIALS, "Email or password is incorrect");
	}
}
