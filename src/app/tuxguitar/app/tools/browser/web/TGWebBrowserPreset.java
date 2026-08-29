package app.tuxguitar.app.tools.browser.web;

public class TGWebBrowserPreset {
	private final String name;
	private final String description;
	private final String url;
	private final boolean followLinks;
	private final String songPattern;

	public TGWebBrowserPreset(String name, String description, String url, boolean followLinks, String songPattern) {
		this.name = name;
		this.description = description;
		this.url = url;
		this.followLinks = followLinks;
		this.songPattern = songPattern;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public String getUrl() {
		return this.url;
	}

	public boolean isFollowLinks() {
		return this.followLinks;
	}

	public String getSongPattern() {
		return this.songPattern;
	}
}
