package br.com.planned.api.controller;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.planned.api.dto.LookupsResponse;
import br.com.planned.api.service.LookupService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/lookups")
@Tag(name = "Lookups")
public class LookupController {

	private final LookupService lookupService;

	public LookupController(LookupService lookupService) {
		this.lookupService = lookupService;
	}

	@GetMapping
	@Operation(summary = "The allowed priority, status, and complexity names, each in seed order")
	@ApiResponse(responseCode = "200", description = "The lookup names")
	@ApiResponse(responseCode = "401", description = "UNAUTHORIZED: missing, invalid, or expired token",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	LookupsResponse lookups() {
		return lookupService.lookups();
	}
}
