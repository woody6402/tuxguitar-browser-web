package app.tuxguitar.app.tools.browser.web;

import app.tuxguitar.app.tools.browser.base.TGBrowserElement;

public class TGBrowserElementImpl implements TGBrowserElement {

	private String name;
	private String url;
	private boolean folder;
	private String group;

	public TGBrowserElementImpl(String name, String url) {
		this(name, url, false, null);
	}

	public TGBrowserElementImpl(
			String name,
			String url,
			boolean folder,
			String group) {

		this.name = name;
		this.url = url;
		this.folder = folder;
		this.group = group;
	}

	public String getName() {
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
}
