package io.pulseops.domain.evidence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RunbookView(String service, String alertType, String title, String content) {
}
