/*
 * Copyright 2011 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.wicket.pages;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.apache.wicket.Application;
import org.apache.wicket.Page;
import org.apache.wicket.PageParameters;
import org.apache.wicket.RedirectToUrlException;
import org.apache.wicket.markup.html.CSSPackageResource;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.resources.JavascriptResourceReference;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.protocol.http.RequestUtils;
import org.apache.wicket.protocol.http.WebResponse;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.apache.wicket.request.target.basic.RedirectRequestTarget;
import org.apache.wicket.util.time.Duration;
import org.apache.wicket.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.Constants.FederationStrategy;
import com.gitblit.Keys;
import com.gitblit.models.ProjectModel;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;

/**
	Wicket WebPage page providing common services
	for GitBlit purposes.
*/
public abstract class BasePage extends SessionPage {

	/** Lazy initialized with {@link #logger} */
	private transient Logger logger;	//Note: this field could be static final which would be 
						//possibly faster and better, but in such case loggers
						//would be bound with BasePage.class and there would
						//be no possibility to filter log data on per-class.
						//It might be better (faster) but some code: 
						//	private static final Logger logger = LoggerFactory.getLogger(x.class)
						//	private static final boolean TRACE = logger.isTraceEnabled();
						//should be copied into each class. I think, it would be better, but it is 
						//a matter of personal choice.
	/** Keeps TRACE logging status. Set up in {@link #getLogger}
	first call. Zero means "unknown", "1" disabled and "2" enabled */
	private transient byte is_Trace_Enabled;
	/** See {@link #is_Trace_Enabled} */
	private transient byte is_Debug_Enabled;
	/** See {@link #is_Trace_Enabled} */
	private transient byte is_Info_Enabled;
	/** See {@link #is_Trace_Enabled} */
	private transient byte is_Warn_Enabled;
	/** See {@link #is_Trace_Enabled} */
	private transient byte is_Error_Enabled;

	/** Lazy initialized with {@link #getTimeUtils} */
	private transient TimeUtils timeUtils;

	public BasePage() {
		super();
		customizeHeader();
	}

	public BasePage(PageParameters params) {
		super(params);
		customizeHeader();
	}

	/**
		Returns logger associated with <code>this.getClass()</code>
		If logger is not already present it creates it at first invocation.
		<p>
		Notes about logging performance.
		<p>
		Use below pattern for WARN and ERROR levels.
		<pre>
			logger().warn("message");
			logger().error("message");
		</pre>
		<p>
		Logging at level WARN or ERROR is usually rare and even a costly
		process of formulating text does not impact performance.
		Logging at levels TRACE, DEBUG, and INFO are few order of magnitudes
		denser and especially trace may harm performance.
		<p>
		To maximize the performance creation of log message must be avoided
		if log level is not enabled. User may use:
		<pre>
			if (logger().isTraceEnabled()) logger().trace("xxx");
		</pre>
		but this will cost at least a sequence of 5 interface calls which 
		are not very fast.
		<p>
		To avoid this burden and ecourage users to use extensive logging
		this class is providing own {@link #isTraceEnabled},
		{@link #isDebugEnabled}, {@link #isInfoEnabled}, {@link #isWarnEnabled},
		{@link #isErrorEnabled} methods which are effectively a single field read.
		<p>
		Following pattern is recommended:
		<pre>
			if (isTraceEnable()) logger().trace(<i>complex message</i>);
		</prE>
		
		@return a logger instance. The underlying library is <u>not specifiying</u>
			if it may return null or not, but at least one of underlying 
			implementations is said to never return a null. Life time constant.
	*/
	protected Logger logger() {
		if (logger == null) {
			logger = LoggerFactory.getLogger(getClass());
			assert(logger!=null);
			is_Trace_Enabled = logger.isTraceEnabled() ? (byte)2 : (byte)1;
			is_Debug_Enabled = logger.isDebugEnabled() ? (byte)2 : (byte)1;
			is_Info_Enabled = logger.isInfoEnabled() ? (byte)2 : (byte)1;
			is_Warn_Enabled = logger.isWarnEnabled() ? (byte)2 : (byte)1;
			is_Error_Enabled = logger.isErrorEnabled() ? (byte)2 : (byte)1;
		}
		return logger;
	}
	/** A fastest possible test if TRACE logging is enabled
	@return true if enable, false if not. Life time constant.
	@see #logger
	*/
	protected final boolean isTraceEnabled()
	{
		switch(is_Trace_Enabled)
		{
			case 1: return false;
			case 2: return true;
			default:
				logger();
				assert(	(is_Trace_Enabled==1)||(is_Trace_Enabled==2));
				return isTraceEnabled();
		}
	};
	
	/** A fastest possible test if Debug logging is enabled
	@return true if enable, false if not. Life time constant.
	@see #logger
	*/
	protected final boolean isDebugEnabled()
	{
		switch(is_Debug_Enabled)
		{
			case 1: return false;
			case 2: return true;
			default:
				logger();
				assert(	(is_Debug_Enabled==1)||(is_Debug_Enabled==2));
				return isDebugEnabled();
		}
	};
	
	/** A fastest possible test if Info logging is enabled
	@return true if enable, false if not. Life time constant.
	@see #logger
	*/
	protected final boolean isInfoEnabled()
	{
		switch(is_Info_Enabled)
		{
			case 1: return false;
			case 2: return true;
			default:
				logger();
				assert(	(is_Info_Enabled==1)||(is_Info_Enabled==2));
				return isInfoEnabled();
		}
	};
	
	/** A fastest possible test if Warn logging is enabled
	@return true if enable, false if not. Life time constant.
	@see #logger
	*/
	protected final boolean isWarnEnabled()
	{
		switch(is_Warn_Enabled)
		{
			case 1: return false;
			case 2: return true;
			default:
				logger();
				assert(	(is_Warn_Enabled==1)||(is_Warn_Enabled==2));
				return isWarnEnabled();
		}
	};
	
	/** A fastest possible test if Error logging is enabled
	@return true if enable, false if not. Life time constant.
	@see #logger
	*/
	protected final boolean isErrorEnabled()
	{
		switch(is_Error_Enabled)
		{
			case 1: return false;
			case 2: return true;
			default:
				logger();
				assert(	(is_Error_Enabled==1)||(is_Error_Enabled==2));
				return isErrorEnabled();
		}
	};

	private void customizeHeader() {
		if (app().settings().getBoolean(Keys.web.useResponsiveLayout, true)) {
			add(CSSPackageResource.getHeaderContribution("bootstrap/css/bootstrap-responsive.css"));
		}
		if (app().settings().getBoolean(Keys.web.hideHeader, false)) {
			add(CSSPackageResource.getHeaderContribution("hideheader.css"));
		}
	}

	protected String getContextUrl() {
		return getRequest().getRelativePathPrefixToContextRoot();
	}

	protected String getCanonicalUrl() {
		return getCanonicalUrl(getClass(), getPageParameters());
	}

	protected String getCanonicalUrl(Class<? extends BasePage> clazz, PageParameters params) {
		String relativeUrl = urlFor(clazz, params).toString();
		String canonicalUrl = RequestUtils.toAbsolutePath(relativeUrl);
		return canonicalUrl;
	}

	protected void redirectTo(Class<? extends BasePage> pageClass) {
		redirectTo(pageClass, null);
	}

	protected void redirectTo(Class<? extends BasePage> pageClass, PageParameters parameters) {
		String absoluteUrl = getCanonicalUrl(pageClass, parameters);
		getRequestCycle().setRequestTarget(new RedirectRequestTarget(absoluteUrl));
	}

	protected String getLanguageCode() {
		return GitBlitWebSession.get().getLocale().getLanguage();
	}

	protected String getCountryCode() {
		return GitBlitWebSession.get().getLocale().getCountry().toLowerCase();
	}

	protected TimeUtils getTimeUtils() {
		if (timeUtils == null) {
			ResourceBundle bundle;
			try {
				bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp", GitBlitWebSession.get().getLocale());
			} catch (Throwable t) {
				bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp");
			}
			timeUtils = new TimeUtils(bundle, getTimeZone());
		}
		return timeUtils;
	}

	@Override
	protected void onBeforeRender() {
		if (app().isDebugMode()) {
			// strip Wicket tags in debug mode for jQuery DOM traversal
			Application.get().getMarkupSettings().setStripWicketTags(true);
		}
		super.onBeforeRender();
	}

	@Override
	protected void onAfterRender() {
		if (app().isDebugMode()) {
			// restore Wicket debug tags
			Application.get().getMarkupSettings().setStripWicketTags(false);
		}
		super.onAfterRender();
	}

	@Override
	protected void setHeaders(WebResponse response)	{
		// set canonical link as http header for SEO (issue-304)
		// https://support.google.com/webmasters/answer/139394?hl=en
		response.setHeader("Link", MessageFormat.format("<{0}>; rel=\"canonical\"", getCanonicalUrl()));
		int expires = app().settings().getInteger(Keys.web.pageCacheExpires, 0);
		if (expires > 0) {
			// pages are personalized for the authenticated user so they must be
			// marked private to prohibit proxy servers from caching them
			response.setHeader("Cache-Control", "private, must-revalidate");
			setLastModified();
		} else {
			// use default Wicket caching behavior
			super.setHeaders(response);
		}

		// XRF vulnerability. issue-500 / ticket-166
		response.setHeader("X-Frame-Options", "SAMEORIGIN");
	}

	/**
	 * Sets the last-modified header date, if appropriate, for this page.  The
	 * date used is determined by the CacheControl annotation.
	 *
	 */
	protected void setLastModified() {
		if (getClass().isAnnotationPresent(CacheControl.class)) {
			CacheControl cacheControl = getClass().getAnnotation(CacheControl.class);
			switch (cacheControl.value()) {
			case ACTIVITY:
				setLastModified(app().getLastActivityDate());
				break;
			case BOOT:
				setLastModified(app().getBootDate());
				break;
			case NONE:
				break;
			default:
				logger().warn(getClass().getSimpleName() + ": unhandled LastModified type " + cacheControl.value());
				break;
			}
		}
	}

	/**
	 * Sets the last-modified header field and the expires field.
	 *
	 * @param when
	 */
	protected final void setLastModified(Date when) {
		if (when == null) {
			return;
		}

		if (when.before(app().getBootDate())) {
			// last-modified can not be before the Gitblit boot date
			// this helps ensure that pages are properly refreshed after a
			// server config change
			when = app().getBootDate();
		}

		int expires = app().settings().getInteger(Keys.web.pageCacheExpires, 0);
		WebResponse response = (WebResponse) getResponse();
		response.setLastModifiedTime(Time.valueOf(when));
		response.setDateHeader("Expires", System.currentTimeMillis() + Duration.minutes(expires).getMilliseconds());
	}

	protected String getPageTitle(String repositoryName) {
		String siteName = app().settings().getString(Keys.web.siteName, Constants.NAME);
		if (StringUtils.isEmpty(siteName)) {
			siteName = Constants.NAME;
		}
		if (repositoryName != null && repositoryName.trim().length() > 0) {
			return repositoryName + " - " + siteName;
		} else {
			return siteName;
		}
	}

	protected void setupPage(String repositoryName, String pageName) {
		add(new Label("title", getPageTitle(repositoryName)));
		getBottomScriptContainer();
		String rootLinkUrl = app().settings().getString(Keys.web.rootLink, urlFor(GitBlitWebApp.get().getHomePage(), null).toString());
		ExternalLink rootLink = new ExternalLink("rootLink", rootLinkUrl);
		WicketUtils.setHtmlTooltip(rootLink, app().settings().getString(Keys.web.siteName, Constants.NAME));
		add(rootLink);

		// Feedback panel for info, warning, and non-fatal error messages
		add(new FeedbackPanel("feedback"));

		add(new Label("gbVersion", "v" + Constants.getVersion()));
		if (app().settings().getBoolean(Keys.web.aggressiveHeapManagement, false)) {
			System.gc();
		}
	}

	protected Map<AccessRestrictionType, String> getAccessRestrictions() {
		Map<AccessRestrictionType, String> map = new LinkedHashMap<AccessRestrictionType, String>();
		for (AccessRestrictionType type : AccessRestrictionType.values()) {
			switch (type) {
			case NONE:
				map.put(type, getString("gb.notRestricted"));
				break;
			case PUSH:
				map.put(type, getString("gb.pushRestricted"));
				break;
			case CLONE:
				map.put(type, getString("gb.cloneRestricted"));
				break;
			case VIEW:
				map.put(type, getString("gb.viewRestricted"));
				break;
			}
		}
		return map;
	}

	protected Map<AccessPermission, String> getAccessPermissions() {
		Map<AccessPermission, String> map = new LinkedHashMap<AccessPermission, String>();
		for (AccessPermission type : AccessPermission.values()) {
			switch (type) {
			case NONE:
				map.put(type, MessageFormat.format(getString("gb.noPermission"), type.code));
				break;
			case EXCLUDE:
				map.put(type, MessageFormat.format(getString("gb.excludePermission"), type.code));
				break;
			case VIEW:
				map.put(type, MessageFormat.format(getString("gb.viewPermission"), type.code));
				break;
			case CLONE:
				map.put(type, MessageFormat.format(getString("gb.clonePermission"), type.code));
				break;
			case PUSH:
				map.put(type, MessageFormat.format(getString("gb.pushPermission"), type.code));
				break;
			case CREATE:
				map.put(type, MessageFormat.format(getString("gb.createPermission"), type.code));
				break;
			case DELETE:
				map.put(type, MessageFormat.format(getString("gb.deletePermission"), type.code));
				break;
			case REWIND:
				map.put(type, MessageFormat.format(getString("gb.rewindPermission"), type.code));
				break;
			}
		}
		return map;
	}

	protected Map<FederationStrategy, String> getFederationTypes() {
		Map<FederationStrategy, String> map = new LinkedHashMap<FederationStrategy, String>();
		for (FederationStrategy type : FederationStrategy.values()) {
			switch (type) {
			case EXCLUDE:
				map.put(type, getString("gb.excludeFromFederation"));
				break;
			case FEDERATE_THIS:
				map.put(type, getString("gb.federateThis"));
				break;
			case FEDERATE_ORIGIN:
				map.put(type, getString("gb.federateOrigin"));
				break;
			}
		}
		return map;
	}

	protected Map<AuthorizationControl, String> getAuthorizationControls() {
		Map<AuthorizationControl, String> map = new LinkedHashMap<AuthorizationControl, String>();
		for (AuthorizationControl type : AuthorizationControl.values()) {
			switch (type) {
			case AUTHENTICATED:
				map.put(type, getString("gb.allowAuthenticatedDescription"));
				break;
			case NAMED:
				map.put(type, getString("gb.allowNamedDescription"));
				break;
			}
		}
		return map;
	}

	protected TimeZone getTimeZone() {
		return app().settings().getBoolean(Keys.web.useClientTimezone, false) ? GitBlitWebSession.get()
				.getTimezone() : app().getTimezone();
	}

	protected String getServerName() {
		ServletWebRequest servletWebRequest = (ServletWebRequest) getRequest();
		HttpServletRequest req = servletWebRequest.getHttpServletRequest();
		return req.getServerName();
	}

	protected List<ProjectModel> getProjectModels() {
		final UserModel user = GitBlitWebSession.get().getUser();
		List<ProjectModel> projects = app().projects().getProjectModels(user, true);
		return projects;
	}

	protected List<ProjectModel> getProjects(PageParameters params) {
		if (params == null) {
			return getProjectModels();
		}

		boolean hasParameter = false;
		String regex = WicketUtils.getRegEx(params);
		String team = WicketUtils.getTeam(params);
		int daysBack = params.getInt("db", 0);
		int maxDaysBack = app().settings().getInteger(Keys.web.activityDurationMaximum, 30);

		List<ProjectModel> availableModels = getProjectModels();
		Set<ProjectModel> models = new HashSet<ProjectModel>();

		if (!StringUtils.isEmpty(regex)) {
			// filter the projects by the regex
			hasParameter = true;
			Pattern pattern = Pattern.compile(regex);
			for (ProjectModel model : availableModels) {
				if (pattern.matcher(model.name).find()) {
					models.add(model);
				}
			}
		}

		if (!StringUtils.isEmpty(team)) {
			// filter the projects by the specified teams
			hasParameter = true;
			List<String> teams = StringUtils.getStringsFromValue(team, ",");

			// need TeamModels first
			List<TeamModel> teamModels = new ArrayList<TeamModel>();
			for (String name : teams) {
				TeamModel teamModel = app().users().getTeamModel(name);
				if (teamModel != null) {
					teamModels.add(teamModel);
				}
			}

			// brute-force our way through finding the matching models
			for (ProjectModel projectModel : availableModels) {
				for (String repositoryName : projectModel.repositories) {
					for (TeamModel teamModel : teamModels) {
						if (teamModel.hasRepositoryPermission(repositoryName)) {
							models.add(projectModel);
						}
					}
				}
			}
		}

		if (!hasParameter) {
			models.addAll(availableModels);
		}

		// time-filter the list
		if (daysBack > 0) {
			if (maxDaysBack > 0 && daysBack > maxDaysBack) {
				daysBack = maxDaysBack;
			}
			Calendar cal = Calendar.getInstance();
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			cal.add(Calendar.DATE, -1 * daysBack);
			Date threshold = cal.getTime();
			Set<ProjectModel> timeFiltered = new HashSet<ProjectModel>();
			for (ProjectModel model : models) {
				if (model.lastChange.after(threshold)) {
					timeFiltered.add(model);
				}
			}
			models = timeFiltered;
		}

		List<ProjectModel> list = new ArrayList<ProjectModel>(models);
		Collections.sort(list);
		return list;
	}

	public void warn(String message, Throwable t) {
		logger().warn(message, t);
	}

	public void error(String message, boolean redirect) {
		error(message, null, redirect ? getApplication().getHomePage() : null);
	}

	public void error(String message, Throwable t, boolean redirect) {
		error(message, t, getApplication().getHomePage());
	}

	public void error(String message, Throwable t, Class<? extends Page> toPage) {
		error(message, t, toPage, null);
	}

	public void error(String message, Throwable t, Class<? extends Page> toPage, PageParameters params) {
		if (t == null) {
			logger().error(message  + " for " + GitBlitWebSession.get().getUsername());
		} else {
			logger().error(message  + " for " + GitBlitWebSession.get().getUsername(), t);
		}
		if (toPage != null) {
			GitBlitWebSession.get().cacheErrorMessage(message);
			String relativeUrl = urlFor(toPage, params).toString();
			String absoluteUrl = RequestUtils.toAbsolutePath(relativeUrl);
			throw new RedirectToUrlException(absoluteUrl);
		} else {
			super.error(message);
		}
	}

	public void authenticationError(String message) {
		logger().error(getRequest().getURL() + " for " + GitBlitWebSession.get().getUsername());
		if (!GitBlitWebSession.get().isLoggedIn()) {
			// cache the request if we have not authenticated.
			// the request will continue after authentication.
			GitBlitWebSession.get().cacheRequest(getClass());
		}
		error(message, true);
	}

	protected String readResource(String resource) {
		StringBuilder sb = new StringBuilder();
		InputStream is = null;
		try {
			is = getClass().getResourceAsStream(resource);
			List<String> lines = IOUtils.readLines(is);
			for (String line : lines) {
				sb.append(line).append('\n');
			}
		} catch (IOException e) {

		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
		}
		return sb.toString();
	}

	private RepeatingView getBottomScriptContainer() {
		RepeatingView bottomScriptContainer = (RepeatingView) get("bottomScripts");
		if (bottomScriptContainer == null) {
			bottomScriptContainer = new RepeatingView("bottomScripts");
			bottomScriptContainer.setRenderBodyOnly(true);
			add(bottomScriptContainer);
		}
		return bottomScriptContainer;
	}

	/**
	 * Adds a HTML script element loading the javascript designated by the given path.
	 *
	 * @param scriptPath
	 *            page-relative path to the Javascript resource; normally starts with "scripts/"
	 */
	protected void addBottomScript(String scriptPath) {
		RepeatingView bottomScripts = getBottomScriptContainer();
		Label script = new Label(bottomScripts.newChildId(), "<script type='text/javascript' src='"
				+ urlFor(new JavascriptResourceReference(this.getClass(), scriptPath)) + "'></script>\n");
		bottomScripts.add(script.setEscapeModelStrings(false).setRenderBodyOnly(true));
	}

	/**
	 * Adds a HTML script element containing the given code.
	 *
	 * @param code
	 *            inline script code
	 */
	protected void addBottomScriptInline(String code) {
		RepeatingView bottomScripts = getBottomScriptContainer();
		Label script = new Label(bottomScripts.newChildId(),
				"<script type='text/javascript'>/*<![CDATA[*/\n" + code + "\n//]]>\n</script>\n");
		bottomScripts.add(script.setEscapeModelStrings(false).setRenderBodyOnly(true));
	}

}
