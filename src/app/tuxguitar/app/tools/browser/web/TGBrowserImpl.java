package app.tuxguitar.app.tools.browser.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.tools.browser.base.TGBrowser;
import app.tuxguitar.app.tools.browser.base.TGBrowserElement;
import app.tuxguitar.app.util.TGMessageDialogUtil;
import app.tuxguitar.app.view.dialog.browser.main.TGBrowserDialog;
import app.tuxguitar.app.view.dialog.message.TGMessageDialog;
import app.tuxguitar.io.base.TGFileFormatManager;
import app.tuxguitar.io.base.TGFileFormatUtils;
import app.tuxguitar.tools.browser.base.TGBrowserCallBack;
import app.tuxguitar.util.TGContext;

public class TGBrowserImpl implements TGBrowser {

	private static final int MAX_HTML_SIZE = 5 * 1024 * 1024;
	private static final int MAX_LINK_COUNT = 10000;

	/*
	 * Web Collection Browser
	 *
	 * Features:
	 *
	 * - loads an HTML page
	 * - extracts <a href="..."> links
	 * - recognizes files supported by registered TuxGuitar readers
	 * - groups files into virtual A-Z folders
	 * - optionally recognizes HTML sub-pages as real web folders
	 * - optionally allows navigation through web folders
	 *
	 * Folder types:
	 *
	 * Virtual A-Z folder:
	 *     folder = true
	 *     group  = "A"
	 *     url    = null
	 *
	 * Web folder:
	 *     folder = true
	 *     group  = null
	 *     url    = "https://..."
	 */

	private static final Pattern LINK_PATTERN = Pattern.compile(
		"<a\\b[^>]*\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))[^>]*>(.*?)</a>",
		Pattern.CASE_INSENSITIVE | Pattern.DOTALL
	);

	private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");

	private TGContext context;
	private TGBrowserSettingsModel data;

	private URI rootUri;
	private URI currentUri;

	/*
	 * Current virtual A-Z folder.
	 *
	 * null means that we are directly on currentUri.
	 */
	private String currentGroup;

	/*
	 * Contents of currentUri.
	 *
	 * Contains:
	 * - song files
	 * - optionally real web folders
	 */
	private List<TGBrowserElement> cachedElements;

	/*
	 * Navigation history for real HTML pages.
	 *
	 * A-Z navigation does NOT use this stack.
	 */
	private Deque<URI> history;
	private boolean followLinks;
	private boolean linkLimitReached;

	public TGBrowserImpl(TGContext context, TGBrowserSettingsModel data) {
		this.context = context;
		this.data = data;

		this.currentGroup = null;
		this.cachedElements = null;
		this.history = new ArrayDeque<URI>();
	}

	/*
	 * ---------------------------------------------------------
	 * Browser lifecycle
	 * ---------------------------------------------------------
	 */

  public void open(TGBrowserCallBack<Object> cb) {
	  try {
		  String url = this.data.getRootUrl();

		  this.followLinks = url.contains("followLinks=true");
		  url = url.replace("?followLinks=true", "");
		  url = url.replace("&followLinks=true", "");

		  this.rootUri = normalizeRoot(url);
		  this.currentUri = this.rootUri;

		  this.currentGroup = null;
		  this.cachedElements = null;

		  this.history.clear();

		  cb.onSuccess(null);
	  } catch (Throwable throwable) {
		  cb.handleError(throwable);
	  }
  }

	public void close(TGBrowserCallBack<Object> cb) {
		this.currentUri = null;
		this.currentGroup = null;
		this.cachedElements = null;

		this.history.clear();

		cb.onSuccess(null);
	}

	/*
	 * ---------------------------------------------------------
	 * Navigation
	 * ---------------------------------------------------------
	 */

	public void cdRoot(TGBrowserCallBack<Object> cb) {
		this.currentUri = this.rootUri;
		this.currentGroup = null;
		this.cachedElements = null;

		this.history.clear();

		cb.onSuccess(null);
	}

	public void cdUp(TGBrowserCallBack<Object> cb) {
		/*
		 * First leave a virtual A-Z folder.
		 *
		 * Example:
		 *
		 * Sor page
		 *   -> S
		 *      -> song.mid
		 *
		 * "Up" from S only returns to the Sor page.
		 */
		if (this.currentGroup != null) {
			this.currentGroup = null;
			cb.onSuccess(null);
			return;
		}

		/*
		 * Otherwise leave the current real HTML page.
		 */
		if (!this.history.isEmpty()) {
			this.currentUri = this.history.pop();
			this.cachedElements = null;
		}

		cb.onSuccess(null);
	}

	public void cdElement(TGBrowserCallBack<Object> cb, TGBrowserElement element) {
		try {
			if (element != null && element.isFolder()) {
				TGBrowserElementImpl webElement = (TGBrowserElementImpl) element;

				/*
				 * Virtual A-Z folder.
				 */
				if (webElement.getGroup() != null) {
					this.currentGroup = webElement.getGroup();
				}

				/*
				 * Real HTML sub-page.
				 *
				 * Web navigation is only available if explicitly enabled
				 * in the browser settings.
				 */
				else if (this.followLinks && webElement.getUrl() != null) {
					URI nextUri = URI.create(webElement.getUrl());

					this.history.push(this.currentUri);
					this.currentUri = nextUri;

					this.currentGroup = null;
					this.cachedElements = null;
				}
			}

			cb.onSuccess(null);
		} catch (Throwable throwable) {
			cb.handleError(throwable);
		}
	}

	/*
	 * ---------------------------------------------------------
	 * Listing
	 * ---------------------------------------------------------
	 */

	public void listElements(TGBrowserCallBack<List<TGBrowserElement>> cb) {
		try {
			/*
			 * Scan current HTML page only once.
			 */
			if (this.cachedElements == null) {
				this.cachedElements = scanPage(this.currentUri);
			}

			/*
			 * Virtual Links directory.
			 */
			if (this.followLinks && "__LINKS__".equals(this.currentGroup)) {
				cb.onSuccess(createLinkElements(true));
				return;
			}

			if (this.followLinks && "__EXTERNAL_LINKS__".equals(this.currentGroup)) {
				cb.onSuccess(createLinkElements(false));
				return;
			}

			/*
			 * Virtual A-Z directory.
			 */
			if (this.currentGroup != null) {
				cb.onSuccess(createFileElements(this.currentGroup));
				return;
			}

			/*
			 * Normal HTML page.
			 *
			 * Show:
			 *
			 * 1. virtual A-Z directories
			 * 2. optional Links directory
			 */
			List<TGBrowserElement> result = new ArrayList<TGBrowserElement>();

			result.addAll(createGroupElements());

			if (this.followLinks) {
				boolean hasInternalLinks = false;
				boolean hasExternalLinks = false;

				for (TGBrowserElement element : this.cachedElements) {
					if (element.isFolder()) {
						Boolean sameHost = ((TGBrowserElementImpl) element).isSameHost();

						if (Boolean.TRUE.equals(sameHost)) {
							hasInternalLinks = true;
						} else if (Boolean.FALSE.equals(sameHost)) {
							hasExternalLinks = true;
						}
					}
				}

				if (hasInternalLinks) {
					result.add(new TGBrowserElementImpl("Links", null, true, "__LINKS__"));
				}

				if (hasExternalLinks) {
					result.add(new TGBrowserElementImpl("Links extern", null, true, "__EXTERNAL_LINKS__"));
				}
			}

			cb.onSuccess(result);

			if (this.linkLimitReached) {
				this.linkLimitReached = false;
				showLinkLimitWarning();
			}
		} catch (Throwable throwable) {
			cb.handleError(throwable);
		}
	}

	/*
	 * ---------------------------------------------------------
	 * HTML scanner
	 * ---------------------------------------------------------
	 */

	private List<TGBrowserElement> scanPage(URI pageUri) throws Exception {
		String html = loadText(pageUri);
		int linkCount = 0;

		this.linkLimitReached = false;

		List<TGBrowserElement> elements = new ArrayList<TGBrowserElement>();
		Set<String> seenUrls = new HashSet<String>();

		Matcher matcher = LINK_PATTERN.matcher(html);

		while (matcher.find()) {
			if (++linkCount > MAX_LINK_COUNT) {
				this.linkLimitReached = true;
				break;
			}

			String href = firstNonNull(
				matcher.group(1),
				matcher.group(2),
				matcher.group(3)
			);

			if (href == null || href.trim().length() == 0) {
				continue;
			}

			href = decodeHtmlEntities(href.trim());

			/*
			 * Ignore pure page anchors.
			 *
			 * Example:
			 *
			 * #A
			 * #top
			 */
			if (href.startsWith("#")) {
				continue;
			}

			URI target;

			try {
				target = resolveHref(pageUri, href);
			} catch (Throwable throwable) {
				continue;
			}

			if (!isHttpUrl(target)) {
				continue;
			}

			String normalizedUrl = target.normalize().toString();

			if (!seenUrls.add(normalizedUrl)) {
				continue;
			}

			/*
			 * -------------------------------------------------
			 * Song file
			 * -------------------------------------------------
			 */
			if (isSupportedSongUrl(target)) {
				String label = fileNameFromUri(target);
				boolean sameHost = isSameHost(pageUri, target);

				elements.add(
					new TGBrowserElementImpl(
						label,
						normalizedUrl,
						false,
						null,
						Boolean.valueOf(sameHost)
					)
				);

				continue;
			}

			/*
			 * -------------------------------------------------
			 * HTML page / real web folder
			 * -------------------------------------------------
			 *
			 * Sub-pages are only collected if the user has
			 * explicitly enabled "Follow links to sub-pages".
			 */
			if (this.followLinks && isBrowsablePage(target)) {
				String label = cleanLabel(matcher.group(4));
				boolean sameHost = isSameHost(pageUri, target);

				/*
				 * If visible link text is empty,
				 * use the URL filename.
				 */
				if (label.length() == 0) {
					label = pageNameFromUri(target);
				}

				elements.add(
					new TGBrowserElementImpl(
						label,
						normalizedUrl,
						true,
						null,
						Boolean.valueOf(sameHost)
					)
				);
			}
		}

		/*
		 * Sort folders/files alphabetically.
		 */
		Collections.sort(
			elements,
			new Comparator<TGBrowserElement>() {
				public int compare(TGBrowserElement a, TGBrowserElement b) {
					/*
					 * Real folders first.
					 */
					if (a.isFolder() && !b.isFolder()) {
						return -1;
					}

					if (!a.isFolder() && b.isFolder()) {
						return 1;
					}

					return getRawName(a).compareToIgnoreCase(getRawName(b));
				}
			}
		);

		return elements;
	}

	private void showLinkLimitWarning() {
		TGMessageDialogUtil.showMessage(
			this.context,
			TGBrowserDialog.getInstance(this.context).getWindow(),
			TuxGuitar.getProperty("warning"),
			"The page contains too many links. Only the first " + MAX_LINK_COUNT + " links were scanned.",
			TGMessageDialog.STYLE_WARNING
		);
	}

	private List<TGBrowserElement> createLinkElements(boolean sameHost) {
		List<TGBrowserElement> result = new ArrayList<TGBrowserElement>();

		if (!this.followLinks) {
			return result;
		}

		for (TGBrowserElement element : this.cachedElements) {
			if (element.isFolder()
					&& Boolean.valueOf(sameHost).equals(((TGBrowserElementImpl) element).isSameHost())) {
				result.add(element);
			}
		}

		return result;
	}

	/*
	 * ---------------------------------------------------------
	 * Virtual A-Z directories
	 * ---------------------------------------------------------
	 */

	private List<TGBrowserElement> createGroupElements() {
		Set<String> groups = new HashSet<String>();

		/*
		 * Important:
		 *
		 * only files are grouped.
		 * Real HTML folders do not create A-Z groups.
		 */
		for (TGBrowserElement element : this.cachedElements) {
			if (!element.isFolder()) {
				groups.add(getGroup(getRawName(element)));
			}
		}

		List<String> sortedGroups = new ArrayList<String>(groups);
		Collections.sort(sortedGroups);

		List<TGBrowserElement> result = new ArrayList<TGBrowserElement>();

		for (String group : sortedGroups) {
			int count = 0;

			for (TGBrowserElement element : this.cachedElements) {
				if (!element.isFolder() && group.equals(getGroup(getRawName(element)))) {
					count++;
				}
			}

			String label = "[" + group + "] (" + count + ")";

			result.add(
				new TGBrowserElementImpl(
					label,
					null,
					true,
					group
				)
			);
		}

		return result;
	}

	private List<TGBrowserElement> createFileElements(String group) {
		List<TGBrowserElement> result = new ArrayList<TGBrowserElement>();

		for (TGBrowserElement element : this.cachedElements) {
			if (!element.isFolder() && group.equals(getGroup(getRawName(element)))) {
				result.add(element);
			}
		}

		return result;
	}

	private String getGroup(String name) {
		if (name == null || name.length() == 0) {
			return "#";
		}

		char c = Character.toUpperCase(name.charAt(0));

		if (c >= 'A' && c <= 'Z') {
			return String.valueOf(c);
		}

		return "#";
	}

	private String getRawName(TGBrowserElement element) {
		return ((TGBrowserElementImpl) element).getRawName();
	}

	/*
	 * ---------------------------------------------------------
	 * File opening
	 * ---------------------------------------------------------
	 */

	public void getInputStream(TGBrowserCallBack<InputStream> cb, TGBrowserElement element) {
		try {
			TGBrowserElementImpl webElement = (TGBrowserElementImpl) element;
			URL url = URI.create(webElement.getUrl()).toURL();

			InputStream stream = TGFileFormatUtils.getInputStream(url.openStream());

			cb.onSuccess(stream);
		} catch (Throwable throwable) {
			cb.handleError(throwable);
		}
	}

	/*
	 * ---------------------------------------------------------
	 * Link resolving
	 * ---------------------------------------------------------
	 */

	private URI resolveHref(URI base, String href) throws Exception {
		/*
		 * Real-world old HTML pages often contain:
		 *
		 * tabs/Alice In Chains - God Am.gp5
		 *
		 * URI requires spaces to be escaped.
		 *
		 * '#' can also legitimately be part of a filename:
		 *
		 * Desert Sessions - A#1.gp5
		 */
		String encodedHref = href
			.replace(" ", "%20")
			.replace("#", "%23");

		return base.resolve(encodedHref);
	}

	/*
	 * ---------------------------------------------------------
	 * Page detection
	 * ---------------------------------------------------------
	 */

	private boolean isBrowsablePage(URI target) {
		String path = target.getPath();

		if (path == null || path.length() == 0) {
			return false;
		}

		String lower = path.toLowerCase();

		/*
		 * Explicit HTML pages.
		 */
		if (lower.endsWith(".html") || lower.endsWith(".htm")) {
			return true;
		}

		/*
		 * Directory-style URLs.
		 *
		 * Example:
		 *
		 * https://site.example/sor/
		 */
		if (lower.endsWith("/")) {
			return true;
		}

		return false;
	}

	private boolean isSameHost(URI source, URI target) {
		return source.getHost() != null
			&& target.getHost() != null
			&& source.getHost().equalsIgnoreCase(target.getHost());
	}

	/*
	 * ---------------------------------------------------------
	 * Root URL
	 * ---------------------------------------------------------
	 */

	private URI normalizeRoot(String value) {
		if (value == null || value.trim().length() == 0) {
			throw new IllegalArgumentException("Root URL is empty");
		}

		URI uri = URI.create(value.trim());

		if (!isHttpUrl(uri)) {
			throw new IllegalArgumentException("Only http:// and https:// URLs are supported");
		}

		return uri;
	}

	/*
	 * ---------------------------------------------------------
	 * HTTP
	 * ---------------------------------------------------------
	 */

	private String loadText(URI uri) throws Exception {
		URLConnection connection = uri.toURL().openConnection();

		connection.setConnectTimeout(10000);
		connection.setReadTimeout(30000);
		connection.setRequestProperty("User-Agent", "TuxGuitar Web Browser");

		try (InputStream input = connection.getInputStream()) {
			byte[] data = new byte[MAX_HTML_SIZE + 1];
			int length = input.readNBytes(data, 0, data.length);

			if (length > MAX_HTML_SIZE) {
				throw new IOException("HTML page is too large");
			}

			return new String(data, 0, length, StandardCharsets.UTF_8);
		}
	}

	/*
	 * ---------------------------------------------------------
	 * TuxGuitar format detection
	 * ---------------------------------------------------------
	 */

	private boolean isSupportedSongUrl(URI uri) {
		String path = uri.getPath();

		if (path == null) {
			return false;
		}

		return TGFileFormatUtils.isSupportedFormat(
			TGFileFormatManager.getInstance(this.context).findReadFileFormats(null),
			path
		);
	}

	private boolean isHttpUrl(URI uri) {
		if (uri == null || uri.getScheme() == null) {
			return false;
		}

		return "http".equalsIgnoreCase(uri.getScheme())
			|| "https".equalsIgnoreCase(uri.getScheme());
	}

	/*
	 * ---------------------------------------------------------
	 * Labels
	 * ---------------------------------------------------------
	 */

	private String cleanLabel(String html) {
		if (html == null) {
			return "";
		}

		String text = TAG_PATTERN.matcher(html).replaceAll(" ");
		text = decodeHtmlEntities(text);

		return text.replaceAll("\\s+", " ").trim();
	}

	private String fileNameFromUri(URI uri) {
		String path = uri.getPath();

		if (path == null || path.length() == 0) {
			return uri.toString();
		}

		int slash = path.lastIndexOf('/');

		return slash >= 0 ? path.substring(slash + 1) : path;
	}

	private String pageNameFromUri(URI uri) {
		String path = uri.getPath();

		if (path == null || path.length() == 0) {
			return uri.toString();
		}

		/*
		 * Remove trailing slash.
		 */
		while (path.endsWith("/") && path.length() > 1) {
			path = path.substring(0, path.length() - 1);
		}

		int slash = path.lastIndexOf('/');
		return slash >= 0 ? path.substring(slash + 1) : path;
	}

	/*
	 * ---------------------------------------------------------
	 * HTML helpers
	 * ---------------------------------------------------------
	 */

	private String decodeHtmlEntities(String value) {
		return value
			.replace("&amp;", "&")
			.replace("&quot;", "\"")
			.replace("&#39;", "'")
			.replace("&lt;", "<")
			.replace("&gt;", ">")
			.replace("&nbsp;", " ");
	}

	private String firstNonNull(String... values) {
		for (String value : values) {
			if (value != null) {
				return value;
			}
		}

		return null;
	}
}
