package app.tuxguitar.app.tools.browser.web;

import app.tuxguitar.app.tools.browser.base.TGBrowserElement;

public class TGBrowserElementImpl implements TGBrowserElement {

	private String name;
	private String url;
	private boolean folder;
	private String group;
	private Boolean sameHost;

	public TGBrowserElementImpl(String name, String url) {
		this(name, url, false, null, null);
	}

	public TGBrowserElementImpl(
			String name,
			String url,
			boolean folder,
			String group) {
		this(name, url, folder, group, null);
	}

	public TGBrowserElementImpl(
			String name,
			String url,
			boolean folder,
			String group,
			Boolean sameHost) {

		this.name = name;
		this.url = url;
		this.folder = folder;
		this.group = group;
		this.sameHost = sameHost;
	}

	public String getName() {
		if (this.sameHost != null) {
			return (this.sameHost.booleanValue() ? "⌂ " : "↗ ") + this.name;
		}

		return this.name;
	}

	public String getRawName() {
		return this.name;
	}

	public String getPath() {
		return this.url;
	}

	public String getUrl() {
		return this.url;
	}

	public boolean isFolder() {
		return this.folder;
	}

	public boolean isSymLink() {
		return false;
	}

	public String getGroup() {
		return this.group;
	}

	public Boolean isSameHost() {
		return this.sameHost;
	}
}
