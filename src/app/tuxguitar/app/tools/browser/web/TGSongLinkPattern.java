package app.tuxguitar.app.tools.browser.web;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TGSongLinkPattern {
	private final Pattern pattern;
	private final int captureCount;

	private TGSongLinkPattern(Pattern pattern, int captureCount) {
		this.pattern = pattern;
		this.captureCount = captureCount;
	}

	public static TGSongLinkPattern compile(String source) {
		if (source == null || source.length() == 0) {
			return null;
		}

		StringBuilder regex = new StringBuilder("^");
		int captureCount = 0;

		for (int i = 0; i < source.length(); i++) {
			if (source.startsWith("{*}", i)) {
				/*
				 * Capturing wildcard for display names. It deliberately stops at
				 * URL separators, so adjacent path components remain separate.
				 */
				regex.append("([^/?#&]+)");
				captureCount++;
				i += 2;
			} else if (source.charAt(i) == '*') {
				regex.append(".*");
			} else if (source.charAt(i) == '?') {
				regex.append('.');
			} else {
				regex.append(Pattern.quote(String.valueOf(source.charAt(i))));
			}
		}

		regex.append('$');
		return new TGSongLinkPattern(
			Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE),
			captureCount
		);
	}

	public TGSongLinkMatch match(String url) {
		Matcher matcher = this.pattern.matcher(url);
		if (!matcher.matches()) {
			return TGSongLinkMatch.NO_MATCH;
		}

		List<String> displayParts = new ArrayList<String>();
		for (int i = 1; i <= this.captureCount; i++) {
			String part = decodeUrlComponent(matcher.group(i));
			if (part.length() > 0) {
				displayParts.add(part);
			}
		}

		return new TGSongLinkMatch(true, join(displayParts, " - "));
	}

	private String decodeUrlComponent(String value) {
		try {
			return URLDecoder.decode(value, "UTF-8").trim();
		} catch (Throwable throwable) {
			return value != null ? value.trim() : "";
		}
	}

	private String join(List<String> values, String separator) {
		StringBuilder result = new StringBuilder();
		for (String value : values) {
			if (result.length() > 0) {
				result.append(separator);
			}
			result.append(value);
		}
		return result.toString();
	}
}
