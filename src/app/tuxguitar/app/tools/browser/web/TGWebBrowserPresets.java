package app.tuxguitar.app.tools.browser.web;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.app.util.TGFileUtils;
import app.tuxguitar.resource.TGResourceManager;
import app.tuxguitar.util.TGContext;

public final class TGWebBrowserPresets {
	private static final String RESOURCE_NAME = "web-browser-presets.json";
	private static final String CONFIG_NAME = "web-browser-presets.json";

	public static final TGWebBrowserPreset CUSTOM = new TGWebBrowserPreset("Custom",
			"Custom source with a freely selectable start address, navigation, and an optional song-link pattern.",
			"", false, "");

	private TGWebBrowserPresets() {
	}

	public static TGWebBrowserPreset[] values(TGContext context) {
		List<TGWebBrowserPreset> presets = new ArrayList<TGWebBrowserPreset>();
		presets.add(CUSTOM);
		File configFile = new File(TGFileUtils.PATH_USER_PLUGINS_CONFIG, CONFIG_NAME);
		try {
			ensureUserConfig(context, configFile);
			presets.addAll(load(configFile.isFile() ? new FileInputStream(configFile) : openDefault(context)));
		} catch (Throwable throwable) {
			System.err.println("Could not load web browser presets from " + configFile.getAbsolutePath()
					+ ": " + throwable.getMessage());
			try {
				presets.addAll(load(openDefault(context)));
			} catch (Throwable fallbackError) {
				System.err.println("Could not load bundled web browser presets: " + fallbackError.getMessage());
			}
		}
		return presets.toArray(new TGWebBrowserPreset[presets.size()]);
	}

	public static String getConfigPath() {
		return new File(TGFileUtils.PATH_USER_PLUGINS_CONFIG, CONFIG_NAME).getAbsolutePath();
	}

	private static void ensureUserConfig(TGContext context, File configFile) throws Exception {
		if (configFile.exists()) return;
		InputStream input = openDefault(context);
		if (input == null) return;
		try {
			OutputStream output = new FileOutputStream(configFile);
			try {
				byte[] buffer = new byte[8192];
				int count;
				while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
			} finally {
				output.close();
			}
		} finally {
			input.close();
		}
	}

	private static InputStream openDefault(TGContext context) {
		return TGResourceManager.getInstance(context).getResourceAsStream(RESOURCE_NAME);
	}

	private static String read(InputStream input) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int count;
		while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
		return new String(output.toByteArray(), StandardCharsets.UTF_8);
	}

	private static List<TGWebBrowserPreset> load(InputStream input) throws Exception {
		if (input == null) throw new IllegalArgumentException("Preset file not found");
		try {
			return new JsonParser(read(input)).parse();
		} finally {
			input.close();
		}
	}

	private static final class JsonParser {
		private final String json;
		private int offset;

		private JsonParser(String json) {
			this.json = json;
		}

		private List<TGWebBrowserPreset> parse() {
			List<TGWebBrowserPreset> result = new ArrayList<TGWebBrowserPreset>();
			expect('[');
			while (!consume(']')) {
				expect('{');
				String name = "";
				String description = "";
				String url = "";
				String songPattern = "";
				boolean followLinks = false;
				while (!consume('}')) {
					String key = string();
					expect(':');
					if ("followLinks".equals(key)) {
						followLinks = bool();
					} else {
						String value = string();
						if ("name".equals(key)) name = value;
						else if ("description".equals(key)) description = value;
						else if ("url".equals(key)) url = value;
						else if ("songPattern".equals(key)) songPattern = value;
					}
					if (!consume(',')) { expect('}'); break; }
				}
				if (name.length() == 0 || url.length() == 0) throw error("Each preset needs name and url");
				result.add(new TGWebBrowserPreset(name, description, url, followLinks, songPattern));
				if (!consume(',')) { expect(']'); break; }
			}
			skipWhitespace();
			if (this.offset != this.json.length()) throw error("Unexpected content");
			return result;
		}

		private boolean bool() {
			skipWhitespace();
			if (this.json.startsWith("true", this.offset)) { this.offset += 4; return true; }
			if (this.json.startsWith("false", this.offset)) { this.offset += 5; return false; }
			throw error("Expected boolean");
		}

		private String string() {
			skipWhitespace();
			expectRaw('"');
			StringBuilder value = new StringBuilder();
			while (this.offset < this.json.length()) {
				char ch = this.json.charAt(this.offset++);
				if (ch == '"') return value.toString();
				if (ch == '\\') {
					if (this.offset >= this.json.length()) throw error("Incomplete escape");
					ch = this.json.charAt(this.offset++);
					if (ch == 'n') value.append('\n');
					else if (ch == 'r') value.append('\r');
					else if (ch == 't') value.append('\t');
					else if (ch == 'b') value.append('\b');
					else if (ch == 'f') value.append('\f');
					else if (ch == 'u') value.append(unicode());
					else if (ch == '"' || ch == '\\' || ch == '/') value.append(ch);
					else throw error("Invalid escape");
				} else value.append(ch);
			}
			throw error("Unterminated string");
		}

		private char unicode() {
			if (this.offset + 4 > this.json.length()) throw error("Incomplete unicode escape");
			try {
				char value = (char)Integer.parseInt(this.json.substring(this.offset, this.offset + 4), 16);
				this.offset += 4;
				return value;
			} catch (NumberFormatException exception) {
				throw error("Invalid unicode escape");
			}
		}

		private boolean consume(char expected) {
			skipWhitespace();
			if (this.offset < this.json.length() && this.json.charAt(this.offset) == expected) {
				this.offset++;
				return true;
			}
			return false;
		}

		private void expect(char expected) {
			if (!consume(expected)) throw error("Expected '" + expected + "'");
		}

		private void expectRaw(char expected) {
			if (this.offset >= this.json.length() || this.json.charAt(this.offset++) != expected) throw error("Expected '" + expected + "'");
		}

		private void skipWhitespace() {
			while (this.offset < this.json.length() && Character.isWhitespace(this.json.charAt(this.offset))) this.offset++;
		}

		private IllegalArgumentException error(String message) {
			return new IllegalArgumentException(message + " at character " + this.offset);
		}
	}
}
