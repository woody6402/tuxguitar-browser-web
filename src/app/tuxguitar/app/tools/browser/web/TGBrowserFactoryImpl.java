package app.tuxguitar.app.tools.browser.web;

import java.net.URI;

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
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
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

		UILabel nameLabel = uiFactory.createLabel(dialog);
		nameLabel.setText("Name");
		layout.set(nameLabel, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, false, false);

    final UITextField nameText = uiFactory.createTextField(dialog);
    layout.set(nameText, 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 1, 420f, null, null);

		UILabel urlLabel = uiFactory.createLabel(dialog);
		urlLabel.setText("Root URL");
		layout.set(urlLabel, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, false, false);

		final UITextField urlText = uiFactory.createTextField(dialog);
		urlText.setText("https://");
		layout.set(urlText, 2, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);

		final UICheckBox followLinks = uiFactory.createCheckBox(dialog);
		followLinks.setText("Follow links to sub-pages");
		followLinks.setSelected(false);
		layout.set(followLinks, 3, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);

    final boolean[] followLinksEnabled = new boolean[] { false };

    followLinks.addSelectionListener(new UISelectionListener() {
	    public void onSelect(UISelectionEvent event) {
		    followLinksEnabled[0] = !followLinksEnabled[0];
	    }
    });

		final UILabel errorLabel = uiFactory.createLabel(dialog);
		errorLabel.setText("");
		layout.set(errorLabel, 4, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);

		UITableLayout buttonsLayout = new UITableLayout(0f);
		UIPanel buttons = uiFactory.createPanel(dialog, false);
		buttons.setLayout(buttonsLayout);
		layout.set(buttons, 5, 1, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_FILL, true, true, 1, 2);

		final UIButton ok = uiFactory.createButton(buttons);
		ok.setText("OK");
		ok.setDefaultButton();
		ok.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				String name = nameText.getText().trim();
				String url = urlText.getText().trim();
				String error = validate(name, url);

				if (error != null) {
					errorLabel.setText(error);
					return;
				}

				dialog.dispose();
				
        if (followLinksEnabled[0]) {
	        url += (url.contains("?") ? "&" : "?") + "followLinks=true";
        }

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

	private String validate(String name, String value) {
		if (name.length() == 0) {
			return "Please enter a name";
		}

		if (value.length() == 0) {
			return "Please enter a root URL";
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
