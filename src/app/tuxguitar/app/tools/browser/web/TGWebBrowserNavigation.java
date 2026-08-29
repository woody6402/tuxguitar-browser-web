package app.tuxguitar.app.tools.browser.web;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.tuxguitar.app.tools.browser.base.TGBrowserElement;

public class TGWebBrowserNavigation {
	private URI rootUri;
	private URI currentUri;
	private String currentGroup;
	private String currentFilePrefix;
	private List<TGBrowserElement> cachedElements;
	private Set<String> inheritedPageLinks;
	private final Deque<PageState> history;

	public TGWebBrowserNavigation() {
		this.history = new ArrayDeque<PageState>();
		this.inheritedPageLinks = new HashSet<String>();
	}

	public void open(URI rootUri) {
		this.rootUri = rootUri;
		goRoot();
	}

	public void close() {
		this.rootUri = null;
		this.currentUri = null;
		this.currentGroup = null;
		this.currentFilePrefix = null;
		this.cachedElements = null;
		this.inheritedPageLinks.clear();
		this.history.clear();
	}

	public void goRoot() {
		this.currentUri = this.rootUri;
		this.currentGroup = null;
		this.currentFilePrefix = null;
		this.cachedElements = null;
		this.inheritedPageLinks.clear();
		this.history.clear();
	}

	public void goUp() {
		if (this.currentFilePrefix != null) {
			this.currentFilePrefix = null;
			return;
		}

		if (this.currentGroup != null) {
			this.currentGroup = null;
			return;
		}

		if (!this.history.isEmpty()) {
			PageState state = this.history.pop();
			this.currentUri = state.getUri();
			this.inheritedPageLinks = state.getInheritedPageLinks();
			this.cachedElements = null;
		}
	}

	public void enterVirtualFolder(String group, String filePrefix) {
		this.currentGroup = group;
		this.currentFilePrefix = filePrefix;
	}

	public void enterPage(URI nextUri, Set<String> currentPageLinks) {
		this.history.push(new PageState(this.currentUri, this.inheritedPageLinks));

		Set<String> nextInheritedLinks = new HashSet<String>(this.inheritedPageLinks);
		if (this.currentUri != null) {
			nextInheritedLinks.add(this.currentUri.normalize().toString());
		}
		if (currentPageLinks != null) {
			nextInheritedLinks.addAll(currentPageLinks);
		}

		this.inheritedPageLinks = nextInheritedLinks;
		this.currentUri = nextUri;
		this.currentGroup = null;
		this.currentFilePrefix = null;
		this.cachedElements = null;
	}

	public URI getCurrentUri() {
		return this.currentUri;
	}

	public String getCurrentGroup() {
		return this.currentGroup;
	}

	public String getCurrentFilePrefix() {
		return this.currentFilePrefix;
	}

	public List<TGBrowserElement> getCachedElements() {
		return this.cachedElements;
	}

	public void setCachedElements(List<TGBrowserElement> cachedElements) {
		this.cachedElements = cachedElements;
	}

	public boolean isInheritedPageLink(String url) {
		return this.inheritedPageLinks.contains(url);
	}

	private static class PageState {
		private final URI uri;
		private final Set<String> inheritedPageLinks;

		private PageState(URI uri, Set<String> inheritedPageLinks) {
			this.uri = uri;
			this.inheritedPageLinks = new HashSet<String>(inheritedPageLinks);
		}

		private URI getUri() {
			return this.uri;
		}

		private Set<String> getInheritedPageLinks() {
			return new HashSet<String>(this.inheritedPageLinks);
		}
	}
}
