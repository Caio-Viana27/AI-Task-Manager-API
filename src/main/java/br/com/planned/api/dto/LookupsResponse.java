package br.com.planned.api.dto;

import java.util.List;

/** The lookup names the UI offers, each list in seed order (wave 2, D3). */
public record LookupsResponse(List<String> priorities, List<String> statuses, List<String> complexities) {
}
