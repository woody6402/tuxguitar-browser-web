package app.tuxguitar.app.tools.browser.web;

public class TGSongLinkMatch {
	public static final TGSongLinkMatch NO_MATCH = new TGSongLinkMatch(false, "");

	private final boolean matches;
	private final String displayName;

	public TGSongLinkMatch(boolean matches, String displayName) {
		this.matches = matches;
		this.displayName = displayName;
	}

	public boolean matches() {
		return this.matches;
	}

	public String getDisplayName() {
		return this.displayName;
	}
}
