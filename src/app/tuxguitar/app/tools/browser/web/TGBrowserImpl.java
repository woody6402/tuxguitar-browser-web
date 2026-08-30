package app.tuxguitar.app.tools.browser.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import app.tuxguitar.util.TGSynchronizer;

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
		"<a\\b[^>]*\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))[^>]*>"
			+ "((?:(?!<a\\b|</a>).)*)(?:</a>)?",
		Pattern.CASE_INSENSITIVE | Pattern.DOTALL
	);

	private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
	private static final Pattern CONTENT_DISPOSITION_FILENAME_PATTERN = Pattern.compile(
		"(?:^|;)\\s*filename\\s*=\\s*(?:\"([^\"]*)\"|([^;]*))",
		Pattern.CASE_INSENSITIVE
	);

	private TGContext context;
	private TGBrowserSettingsModel data;

	private TGWebBrowserNavigation navigation;
	private boolean followLinks;
	private TGSongLinkPattern songLinkPattern;
	private boolean linkLimitReached;

	public TGBrowserImpl(TGContext context, TGBrowserSettingsModel data) {
		this.context = context;
		this.data = data;

		this.navigation = new TGWebBrowserNavigation();
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

		  this.songLinkPattern = null;
		  int patternIndex = url.indexOf("tgSongPattern=");
		  if (patternIndex >= 0) {
			  int valueStart = patternIndex + "tgSongPattern=".length();
			  int valueEnd = url.indexOf('&', valueStart);
			  String encodedPattern = url.substring(valueStart, valueEnd >= 0 ? valueEnd : url.length());
			  this.songLinkPattern = TGSongLinkPattern.compile(URLDecoder.decode(encodedPattern, "UTF-8"));
			  int optionStart = patternIndex > 0 && (url.charAt(patternIndex - 1) == '?' || url.charAt(patternIndex - 1) == '&')
				  ? patternIndex - 1 : patternIndex;
			  url = url.substring(0, optionStart) + (valueEnd >= 0 ? url.substring(valueEnd) : "");
		  }

		  this.navigation.open(normalizeRoot(url));
		  updateWindowTitle();

		  cb.onSuccess(null);
	  } catch (Throwable throwable) {
		  cb.handleError(throwable);
	  }
  }

	public void close(TGBrowserCallBack<Object> cb) {
		this.navigation.close();
		restoreWindowTitle();

		cb.onSuccess(null);
	}

	/*
	 * ---------------------------------------------------------
	 * Navigation
	 * ---------------------------------------------------------
	 */

	public void cdRoot(TGBrowserCallBack<Object> cb) {
		this.navigation.goRoot();
		updateWindowTitle();

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
		this.navigation.goUp();
		updateWindowTitle();

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
					this.navigation.enterVirtualFolder(webElement.getGroup(), webElement.getFilePrefix());
				}

				/*
				 * Real HTML sub-page.
				 *
				 * Web navigation is only available if explicitly enabled
				 * in the browser settings.
				 */
				else if (this.followLinks && webElement.getUrl() != null) {
					URI nextUri = URI.create(webElement.getUrl());

					this.navigation.enterPage(nextUri, collectCurrentPageLinks());
				}
			}
			updateWindowTitle();

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
			if (this.navigation.getCachedElements() == null) {
				this.navigation.setCachedElements(scanPage(this.navigation.getCurrentUri()));
			}

			/*
			 * Virtual Links directory.
			 */
			if (this.followLinks && "__LINKS__".equals(this.navigation.getCurrentGroup())) {
				cb.onSuccess(createLinkElements(true));
				return;
			}

			if (this.followLinks && "__EXTERNAL_LINKS__".equals(this.navigation.getCurrentGroup())) {
				cb.onSuccess(createLinkElements(false));
				return;
			}

			/*
			 * Virtual A-Z directory.
			 */
			if (this.navigation.getCurrentGroup() != null) {
				cb.onSuccess(this.navigation.getCurrentFilePrefix() != null
					? createPrefixFileElements(this.navigation.getCurrentGroup(), this.navigation.getCurrentFilePrefix())
					: createFileElements(this.navigation.getCurrentGroup()));
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
				boolean hasExternalLinks = false;

				for (TGBrowserElement element : this.navigation.getCachedElements()) {
					if (element.isFolder()) {
						Boolean sameHost = ((TGBrowserElementImpl) element).isSameHost();

						if (Boolean.TRUE.equals(sameHost)) {
							result.add(element);
						} else if (Boolean.FALSE.equals(sameHost)) {
							hasExternalLinks = true;
						}
					}
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

	public void searchElements(TGBrowserCallBack<List<TGBrowserElement>> cb, String query) {
		try {
			if (this.navigation.getCachedElements() == null) {
				this.navigation.setCachedElements(scanPage(this.navigation.getCurrentUri()));
			}

			String normalizedQuery = normalizeSearchText(query);
			List<TGBrowserElement> result = new ArrayList<TGBrowserElement>();
			for (TGBrowserElement element : this.navigation.getCachedElements()) {
				if (!element.isFolder() && matchesSearch(getRawName(element), normalizedQuery)) {
					result.add(element);
				}
			}
			cb.onSuccess(result);
		} catch (Throwable throwable) {
			cb.handleError(throwable);
		}
	}

	private boolean matchesSearch(String name, String query) {
		String value = normalizeSearchText(name);
		for (String token : query.split("\\s+")) {
			if (token.length() > 0 && !value.contains(token)) {
				return false;
			}
		}
		return true;
	}

	private String normalizeSearchText(String value) {
		return (value != null ? value : "")
			.toLowerCase(Locale.ROOT)
			.replace('_', ' ')
			.replace('-', ' ');
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
			TGSongLinkMatch songLinkMatch = matchSongLinkPattern(normalizedUrl);
			if (isSupportedSongUrl(target) || songLinkMatch.matches()) {
				String label = isSupportedSongUrl(target) ? fileNameFromUri(target) : songLinkMatch.getDisplayName();
				if (label.length() == 0) {
					label = cleanLabel(matcher.group(4));
				}
				if (label.length() == 0) {
					label = pageNameFromUri(target);
				}
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
			if (this.followLinks && isBrowsablePage(pageUri, target)) {
				/*
				 * Global navigation is commonly repeated on every page. Once a
				 * page link was already present on an ancestor, do not expose it
				 * again on the child page. Song files above deliberately bypass
				 * this filter.
				 */
				if (this.navigation.isInheritedPageLink(normalizedUrl)) {
					continue;
				}

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

	private Set<String> collectCurrentPageLinks() {
		Set<String> result = new HashSet<String>();

		if (this.navigation.getCachedElements() != null) {
			for (TGBrowserElement element : this.navigation.getCachedElements()) {
				if (element.isFolder()) {
					String url = ((TGBrowserElementImpl) element).getUrl();
					if (url != null) {
						result.add(url);
					}
				}
			}
		}

		return result;
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

		for (TGBrowserElement element : this.navigation.getCachedElements()) {
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
		for (TGBrowserElement element : this.navigation.getCachedElements()) {
			if (!element.isFolder()) {
				groups.add(getGroup(getRawName(element)));
			}
		}

		List<String> sortedGroups = new ArrayList<String>(groups);
		Collections.sort(sortedGroups);

		List<TGBrowserElement> result = new ArrayList<TGBrowserElement>();

		for (String group : sortedGroups) {
			int count = 0;

			for (TGBrowserElement element : this.navigation.getCachedElements()) {
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
		Map<String, Integer> prefixCounts = new HashMap<String, Integer>();
		Map<String, String> prefixLabels = new HashMap<String, String>();

		for (TGBrowserElement element : this.navigation.getCachedElements()) {
			if (!element.isFolder() && group.equals(getGroup(getRawName(element)))) {
				String prefix = getFilePrefix(getRawName(element));
				if (prefix != null) {
					String key = prefix.toLowerCase(Locale.ROOT);
					Integer count = prefixCounts.get(key);
					prefixCounts.put(key, Integer.valueOf(count != null ? count.intValue() + 1 : 1));
					if (!prefixLabels.containsKey(key)) {
						prefixLabels.put(key, prefix);
					}
				}
			}
		}

		List<String> groupedPrefixes = new ArrayList<String>();
		for (Map.Entry<String, Integer> entry : prefixCounts.entrySet()) {
			if (entry.getValue().intValue() >= 2) {
				groupedPrefixes.add(entry.getKey());
			}
		}
		Collections.sort(groupedPrefixes, String.CASE_INSENSITIVE_ORDER);

		for (String prefix : groupedPrefixes) {
			result.add(new TGBrowserElementImpl(
				prefixLabels.get(prefix) + " (" + prefixCounts.get(prefix) + ")",
				true,
				group,
				prefix));
		}

		for (TGBrowserElement element : this.navigation.getCachedElements()) {
			if (!element.isFolder() && group.equals(getGroup(getRawName(element)))) {
				String prefix = getFilePrefix(getRawName(element));
				String key = (prefix != null ? prefix.toLowerCase(Locale.ROOT) : null);
				if (key == null || !groupedPrefixes.contains(key)) {
					result.add(element);
				}
			}
		}

		return result;
	}

	private List<TGBrowserElement> createPrefixFileElements(String group, String prefix) {
		List<TGBrowserElement> result = new ArrayList<TGBrowserElement>();

		for (TGBrowserElement element : this.navigation.getCachedElements()) {
			String elementPrefix = getFilePrefix(getRawName(element));
			if (!element.isFolder()
					&& group.equals(getGroup(getRawName(element)))
					&& elementPrefix != null
					&& prefix.equals(elementPrefix.toLowerCase(Locale.ROOT))) {
				result.add(element);
			}
		}

		return result;
	}

	private String getFilePrefix(String name) {
		if (name == null) {
			return null;
		}

		int separator = name.indexOf('_');
		if (separator <= 0 || separator > 40) {
			return null;
		}

		String prefix = name.substring(0, separator).trim();
		return (prefix.length() > 0 ? prefix : null);
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
			URI targetUri = URI.create(webElement.getUrl());
			URLConnection connection = targetUri.toURL().openConnection();

			connection.setConnectTimeout(10000);
			connection.setReadTimeout(30000);
			connection.setRequestProperty("User-Agent", "TuxGuitar Web Browser");

			if (isSameHost(this.navigation.getCurrentUri(), targetUri)) {
				connection.setRequestProperty("Referer", this.navigation.getCurrentUri().toString());
			}

			InputStream responseStream = connection.getInputStream();
			String responseFileName = getResponseFileName(connection);
			if (responseFileName != null) {
				webElement.setName(responseFileName);
			}
			InputStream stream = TGFileFormatUtils.getInputStream(responseStream);

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

	private boolean isBrowsablePage(URI source, URI target) {
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

		/*
		 * Modern sites commonly use extensionless routes.
		 *
		 * Example:
		 *
		 * https://site.example/en/tabs/artist
		 *
		 * Only accept these routes on the same host. This prevents an
		 * extensionless external download or service URL from being exposed as
		 * a normal web folder.
		 */
		int slash = path.lastIndexOf('/');
		String name = (slash >= 0 ? path.substring(slash + 1) : path);

		if (name.length() > 0 && name.indexOf('.') < 0 && isSameHost(source, target)) {
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

	private TGSongLinkMatch matchSongLinkPattern(String url) {
		return this.songLinkPattern != null
			? this.songLinkPattern.match(url)
			: TGSongLinkMatch.NO_MATCH;
	}

	private boolean isHttpUrl(URI uri) {
		if (uri == null || uri.getScheme() == null) {
			return false;
		}

		return "http".equalsIgnoreCase(uri.getScheme())
			|| "https".equalsIgnoreCase(uri.getScheme());
	}

	private String getResponseFileName(URLConnection connection) {
		String disposition = connection.getHeaderField("Content-Disposition");
		if (disposition == null) {
			return null;
		}

		Matcher matcher = CONTENT_DISPOSITION_FILENAME_PATTERN.matcher(disposition);
		if (!matcher.find()) {
			return null;
		}

		String name = firstNonNull(matcher.group(1), matcher.group(2));
		if (name == null) {
			return null;
		}

		name = name.trim();
		name = name.replace('\\', '/');
		int slash = name.lastIndexOf('/');
		if (slash >= 0) {
			name = name.substring(slash + 1);
		}

		return (name.length() > 0 ? name : null);
	}

	/*
	 * ---------------------------------------------------------
	 * Location display
	 * ---------------------------------------------------------
	 */

	private void updateWindowTitle() {
		final URI uri = this.navigation.getCurrentUri();
		final String group = this.navigation.getCurrentGroup();
		final String prefix = this.navigation.getCurrentFilePrefix();

		TGSynchronizer.getInstance(this.context).executeLater(new Runnable() {
			public void run() {
				TGBrowserDialog browser = TGBrowserDialog.getInstance(TGBrowserImpl.this.context);
				if (!browser.isDisposed()) {
					StringBuilder title = new StringBuilder(TuxGuitar.getProperty("browser.dialog"));
					if (uri != null) {
						title.append(" - ").append(uri.toString());
					}
					if (group != null) {
						title.append(" - ").append(displayGroup(group));
					}
					if (prefix != null) {
						title.append(" - ").append(prefix);
					}
					browser.getWindow().setText(title.toString());
				}
			}
		});
	}

	private void restoreWindowTitle() {
		TGSynchronizer.getInstance(this.context).executeLater(new Runnable() {
			public void run() {
				TGBrowserDialog browser = TGBrowserDialog.getInstance(TGBrowserImpl.this.context);
				if (!browser.isDisposed()) {
					browser.getWindow().setText(TuxGuitar.getProperty("browser.dialog"));
				}
			}
		});
	}

	private String displayGroup(String group) {
		if ("__LINKS__".equals(group)) {
			return "Links";
		}
		if ("__EXTERNAL_LINKS__".equals(group)) {
			return "Links extern";
		}
		return group;
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
