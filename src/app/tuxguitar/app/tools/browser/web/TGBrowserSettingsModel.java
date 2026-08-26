package app.tuxguitar.app.tools.browser.web;

import app.tuxguitar.tools.browser.base.TGBrowserSettings;

public class TGBrowserSettingsModel {

	private String name;
	private String rootUrl;

	public TGBrowserSettingsModel(String name, String rootUrl) {
		this.name = name;
		this.rootUrl = rootUrl;
	}

	public String getName() {
		return this.name;
	}

	public String getRootUrl() {
		return this.rootUrl;
	}

	public TGBrowserSettings toBrowserSettings() {
		TGBrowserSettings settings = new TGBrowserSettings();
		settings.setTitle(this.name);
		settings.setData(this.rootUrl);
		return settings;
	}

	public static TGBrowserSettingsModel createInstance(TGBrowserSettings settings) {
		if (settings == null || settings.getData() == null) {
			return null;
		}

		return new TGBrowserSettingsModel(settings.getTitle(), settings.getData());
	}
}
