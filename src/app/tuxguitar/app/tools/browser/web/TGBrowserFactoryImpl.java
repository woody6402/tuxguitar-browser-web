package app.tuxguitar.app.tools.browser.web;

import java.net.URI;
import java.net.URLEncoder;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.tools.browser.base.TGBrowserFactory;
import app.tuxguitar.app.tools.browser.base.TGBrowserFactoryHandler;
import app.tuxguitar.app.ui.TGApplication;
import app.tuxguitar.app.view.dialog.browser.main.TGBrowserDialog;
import app.tuxguitar.app.view.main.TGWindow;
import app.tuxguitar.app.view.util.TGDialogUtil;
import app.tuxguitar.tools.browser.base.TGBrowserFactorySettingsHandler;
import app.tuxguitar.tools.browser.base.TGBrowserSettings;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIImage;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UICheckBox;
import app.tuxguitar.ui.widget.UIDropDownSelect;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIReadOnlyTextBox;
import app.tuxguitar.ui.widget.UISelectItem;
import app.tuxguitar.ui.widget.UITextField;
import app.tuxguitar.ui.widget.UIWindow;
import app.tuxguitar.util.TGContext;

public class TGBrowserFactoryImpl implements TGBrowserFactory {

	private TGContext context;

	public TGBrowserFactoryImpl(TGContext context) {
		this.context = context;
	}

	public String getType() {
		return "web";
	}

	public String getName() {
		return "Web";
	}

	public UIImage getIcon() {
		return TuxGuitar.getInstance().getIconManager().getBrowserFolderRemote();
	}

	public void createBrowser(TGBrowserFactoryHandler handler, TGBrowserSettings settings) {
		TGBrowserSettingsModel model = TGBrowserSettingsModel.createInstance(settings);
		handler.onCreateBrowser(new TGBrowserImpl(this.context, model));
	}

	public void createSettings(TGBrowserFactorySettingsHandler handler) {
		new TGBrowserDataDialog(this.context, handler).show();
	}
}

class TGBrowserDataDialog {
	private TGContext context;
	private TGBrowserFactorySettingsHandler handler;

	public TGBrowserDataDialog(TGContext context, TGBrowserFactorySettingsHandler handler) {
		this.context = context;
		this.handler = handler;
	}

	public void show() {
		TGBrowserDialog browser = TGBrowserDialog.getInstance(this.context);
		show(!browser.isDisposed() ? browser.getWindow() : TGWindow.getInstance(this.context).getWindow());
	}

	private void show(final UIWindow parent) {
		final UIFactory uiFactory = TGApplication.getInstance(this.context).getFactory();
		final UITableLayout layout = new UITableLayout();
		final UIWindow dialog = uiFactory.createWindow(parent, true, false);

		dialog.setLayout(layout);
		dialog.setText("Web Browser");

		UILabel presetLabel = uiFactory.createLabel(dialog);
		presetLabel.setText("Known configuration");
		layout.set(presetLabel, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, false, false);

		final UIDropDownSelect<TGWebBrowserPreset> presetSelect = uiFactory.createDropDownSelect(dialog);
		for (TGWebBrowserPreset preset : TGWebBrowserPresets.values(this.context)) {
			presetSelect.addItem(new UISelectItem<TGWebBrowserPreset>(preset.getName(), preset));
		}
		presetSelect.setSelectedValue(TGWebBrowserPresets.CUSTOM);
		layout.set(presetSelect, 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);

		UILabel descriptionLabel = uiFactory.createLabel(dialog);
		descriptionLabel.setText("Contents");
		layout.set(descriptionLabel, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_TOP, false, false);

		final UIReadOnlyTextBox presetDescription = uiFactory.createReadOnlyTextBox(dialog, false, false);
		presetDescription.setText(TGWebBrowserPresets.CUSTOM.getDescription());
		layout.set(presetDescription, 2, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_TOP, true, false,
				1, 1, 520f, 80f, null);

		UILabel nameLabel = uiFactory.createLabel(dialog);
		nameLabel.setText("Name");
		layout.set(nameLabel, 3, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, false, false);

    final UITextField nameText = uiFactory.createTextField(dialog);
    layout.set(nameText, 3, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 1, 420f, null, null);

		UILabel urlLabel = uiFactory.createLabel(dialog);
		urlLabel.setText("Root URL");
		layout.set(urlLabel, 4, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, false, false);

		final UITextField urlText = uiFactory.createTextField(dialog);
		urlText.setText("https://");
		layout.set(urlText, 4, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);

		final UICheckBox followLinks = uiFactory.createCheckBox(dialog);
		followLinks.setText("Follow links to sub-pages");
		followLinks.setSelected(false);
		layout.set(followLinks, 5, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);

		UILabel songPatternLabel = uiFactory.createLabel(dialog);
		songPatternLabel.setText("Song link pattern");
		layout.set(songPatternLabel, 6, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, false, false);

		final UITextField songPatternText = uiFactory.createTextField(dialog);
		layout.set(songPatternText, 6, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);

		UILabel songPatternHint = uiFactory.createLabel(dialog);
		songPatternHint.setText("Optional; wildcards: *, ? and {*} (display name)");
		layout.set(songPatternHint, 7, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);

		presetSelect.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				TGWebBrowserPreset preset = presetSelect.getSelectedValue();
				presetDescription.setText(preset != null ? preset.getDescription() : "");
				if (preset != null && preset != TGWebBrowserPresets.CUSTOM) {
					nameText.setText(preset.getName());
					urlText.setText(preset.getUrl());
					followLinks.setSelected(preset.isFollowLinks());
					songPatternText.setText(preset.getSongPattern());
				}
			}
		});

		final UILabel errorLabel = uiFactory.createLabel(dialog);
		errorLabel.setText("");
		layout.set(errorLabel, 8, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);

		UITableLayout buttonsLayout = new UITableLayout(0f);
		UIPanel buttons = uiFactory.createPanel(dialog, false);
		buttons.setLayout(buttonsLayout);
		layout.set(buttons, 9, 1, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_FILL, true, true, 1, 2);

		final UIButton ok = uiFactory.createButton(buttons);
		ok.setText("OK");
		ok.setDefaultButton();
		ok.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				String name = nameText.getText().trim();
				String url = urlText.getText().trim();
				String songPattern = songPatternText.getText().trim();
				String error = validate(name, url, songPattern);

				if (error != null) {
					errorLabel.setText(error);
					return;
				}

				if (followLinks.isSelected()) {
	        url += (url.contains("?") ? "&" : "?") + "followLinks=true";
        }

				if (songPattern.length() > 0) {
					try {
						url += (url.contains("?") ? "&" : "?") + "tgSongPattern="
							+ URLEncoder.encode(songPattern, "UTF-8");
					} catch (Throwable throwable) {
						errorLabel.setText("Could not encode song link pattern");
						return;
					}
				}

				dialog.dispose();
        handler.onCreateSettings(new TGBrowserSettingsModel(name, url).toBrowserSettings());
			}
		});
		buttonsLayout.set(ok, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true,
				1, 1, 80f, 25f, null);

		UIButton cancel = uiFactory.createButton(buttons);
		cancel.setText("Cancel");
		cancel.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				dialog.dispose();
			}
		});
		buttonsLayout.set(cancel, 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true,
				1, 1, 80f, 25f, null);

		TGDialogUtil.openDialog(dialog, TGDialogUtil.OPEN_STYLE_CENTER | TGDialogUtil.OPEN_STYLE_PACK);
	}

	private String validate(String name, String value, String songPattern) {
		if (name.length() == 0) {
			return "Please enter a name";
		}

		if (value.length() == 0) {
			return "Please enter a root URL";
		}

		if (songPattern.length() > 500) {
			return "Song link pattern is too long";
		}

		try {
			URI uri = URI.create(value);
			String scheme = uri.getScheme();

			if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
				return "Only http:// and https:// URLs are supported";
			}
		} catch (Throwable throwable) {
			return "Invalid URL";
		}

		return null;
	}
}
