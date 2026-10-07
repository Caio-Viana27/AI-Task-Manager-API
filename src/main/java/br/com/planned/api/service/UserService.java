package br.com.planned.api.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.planned.api.dto.UserResponse;
import br.com.planned.api.exception.ApiException;
import br.com.planned.api.exception.ErrorCode;
import br.com.planned.api.repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/**
	 * @throws ApiException {@code UNAUTHORIZED} if the token's user no longer exists
	 */
	@Transactional(readOnly = true)
	public UserResponse getUser(UUID id) {
		return userRepository.findById(id)
				.map(UserResponse::from)
				.orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Authentication is required"));
	}
}
