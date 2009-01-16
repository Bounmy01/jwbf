/*
 * Copyright 2007 Tobias Knerr.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * Contributors:
 * Tobias Knerr
 *
 */
package net.sourceforge.jwbf.actions.mw.queries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.jwbf.actions.Get;
import net.sourceforge.jwbf.actions.mw.HttpAction;
import net.sourceforge.jwbf.actions.mw.MediaWiki;
import net.sourceforge.jwbf.actions.mw.MultiAction;
import net.sourceforge.jwbf.actions.mw.util.MWAction;
import net.sourceforge.jwbf.actions.mw.util.ProcessException;

import org.apache.log4j.Logger;

/**
 * Action class using the MediaWiki-api's "list=allpages".
 * 
 * @author Tobias Knerr
 * @author Thomas Stock
 *  
 * @supportedBy MediaWikiAPI 1.9, 1.10, 1.11, 1.12, 1.13, 1.14
 * 
 */
public class GetAllPageTitles extends MWAction implements MultiAction<String> {
	private static final Logger LOG = Logger.getLogger(GetAllPageTitles.class);
	/** Pattern to parse returned page, @see {@link #parseHasMore(String)} */
	private static final Pattern HAS_MORE_PATTERN = Pattern
			.compile(
					"<query-continue>.*?<allpages *apfrom=\"([^\"]*)\" */>.*?</query-continue>",
					Pattern.DOTALL | Pattern.MULTILINE);
	private static final Pattern ARTICLE_TITLES_PATTERN = Pattern
			.compile("<p pageid=\".*?\" ns=\".*?\" title=\"(.*?)\" />");
	/** Pattern to parse returned page, @see {@link #parseArticleTitles(String)} */
	/** Constant value for the aplimit-parameter. **/
	private static final int LIMIT = 50;
	/**
	 * Collection that will contain the result (titles of articles matching the
	 * given criteria) after performing the action has finished.
	 */
	private Collection<String> titleCollection = new ArrayList<String>();
	/** Information necessary to get the next api page. */
	private String nextPageInfo = null;
	
	private HttpAction msg;
	/**
	 * Information given in the constructor, necessary for creating next action.
	 */
	private String prefix;
	private String namespace;
	private boolean redirects;
	private boolean nonredirects;

	/**
	 * The public constructor. It will have an MediaWiki-request generated,
	 * which is then added to msgs. When it is answered, the method
	 * processAllReturningText will be called (from outside this class). For the
	 * parameters, see
	 * {@link GetAllPageTitles#generateRequest(String, String, boolean, boolean, String)}
	 * 
	 * @param from
	 *            page title to start from, may be null
	 * @param prefix
	 *            restricts search to titles that begin with this value, may be
	 *            null
	 * @param redirects
	 *            include redirects in the list
	 * @param nonredirects
	 *            include nonredirects in the list (will be ignored if redirects
	 *            is false!)
	 * @param namespaces
	 *            the namespace(s) that will be searched for links, as a string
	 *            of numbers separated by '|'; if null, this parameter is
	 *            omitted
	 */
	public GetAllPageTitles(String from, String prefix, boolean redirects,
			boolean nonredirects, int ... namespaces) {
		this(from, prefix, redirects, nonredirects, createNsString(namespaces));

	}
	
	protected GetAllPageTitles(String from, String prefix, boolean redirects,
			boolean nonredirects, String namespaces) {
		this.prefix = prefix;
		this.namespace = namespaces;
		this.redirects = redirects;
		this.nonredirects = nonredirects;
		generateRequest(from, prefix, redirects, nonredirects, namespace);
	}

	/**
	 * Generates the next MediaWiki-request (GetMethod) and adds it to msgs.
	 * 
	 * @param from
	 *            page title to start from, may be null
	 * @param prefix
	 *            restricts search to titles that begin with this value, may be
	 *            null
	 * @param redirects
	 *            include redirects in the list
	 * @param nonredirects
	 *            include nonredirects in the list (will be ignored if redirects
	 *            is false!)
	 * @param namespace
	 *            the namespace(s) that will be searched for links, as a string
	 *            of numbers separated by '|'; if null, this parameter is
	 *            omitted
	 */
	protected void generateRequest(String from, String prefix,
			boolean redirects, boolean nonredirects, String namespace) {
		if (LOG.isTraceEnabled()) {
			LOG
					.trace("enter GetAllPagetitles.generateRequest(String,String,boolean,boolean,String)");
		}

		String apfilterredir;
		if (redirects && nonredirects) {
			apfilterredir = "all";
		} else if (redirects && !nonredirects) {
			apfilterredir = "redirects";
		} else {
			apfilterredir = "nonredirects";
		}

		String uS = "/api.php?action=query&list=allpages&"
				+ ((from != null) ? ("&apfrom=" + MediaWiki.encode(from)) : "")
				+ ((prefix != null) ? ("&apprefix=" + MediaWiki.encode(prefix))
						: "")
				+ ((namespace != null && namespace.length() != 0) ? ("&apnamespace=" + namespace)
						: "") + "&apfilterredir=" + apfilterredir + "&aplimit="
				+ LIMIT + "&format=xml";
		msg = new Get(uS);

	}

	/**
	 * Deals with the MediaWiki api's response by parsing the provided text.
	 * 
	 * @param s
	 *            the answer to the most recently generated MediaWiki-request
	 * 
	 * @return empty string
	 */
	public String processAllReturningText(final String s)
			throws ProcessException {
		if (LOG.isTraceEnabled()) {
			LOG.trace("enter GetAllPagetitles.processAllReturningText(String)");
		}
		parseArticleTitles(s);
		parseHasMore(s);
		return "";
	}

	/**
	 * Gets the information about a follow-up page from a provided api response.
	 * If there is one, a new request is added to msgs by calling
	 * generateRequest.
	 * 
	 * @param s
	 *            text for parsing
	 */
	protected void parseHasMore(final String s) {
		if (LOG.isTraceEnabled()) {
			LOG.trace("enter GetAllPagetitles.parseHasMore(String)");
		}
		Matcher m = HAS_MORE_PATTERN.matcher(s);
		if (m.find()) {
			nextPageInfo = m.group(1);
		}
	}

	/**
	 * Picks the article name from a MediaWiki api response.
	 * 
	 * @param s
	 *            text for parsing
	 */
	public void parseArticleTitles(String s) {
		if (LOG.isTraceEnabled()) {
			LOG.trace("enter GetAllPagetitles.parseArticleTitles(String)");
		}
		Matcher m = ARTICLE_TITLES_PATTERN.matcher(s);
		while (m.find()) {
			String title = m.group(1);
			if (LOG.isDebugEnabled()) {
				LOG.debug("Found article title: \"" + title + "\"");
			}
			titleCollection.add(title);
		}
	}

	/**
	 * @return the collected article names
	 */
	public Collection<String> getResults() {
		return titleCollection;
	}

	/**
	 * @return necessary information for the next action or null if no next api
	 *         page exists
	 */
	public GetAllPageTitles getNextAction() {
		if (nextPageInfo == null) {
			return null;
		} else {
			return new GetAllPageTitles(nextPageInfo, prefix, redirects,
					nonredirects, namespace);
		}
	}

	public HttpAction getNextMessage() {
		return msg;
	}

}
