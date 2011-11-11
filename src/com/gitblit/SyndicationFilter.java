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
package com.gitblit;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;

/**
 * The SyndicationFilter is an AccessRestrictionFilter which ensures that feed
 * requests for view-restricted repositories have proper authentication
 * credentials and are authorized for the requested feed.
 * 
 * @author James Moger
 * 
 */
public class SyndicationFilter extends AccessRestrictionFilter {

	/**
	 * Extract the repository name from the url.
	 * 
	 * @param url
	 * @return repository name
	 */
	@Override
	protected String extractRepositoryName(String url) {
		if (url.indexOf('?') > -1) {
			return url.substring(0, url.indexOf('?'));
		}
		return url;
	}

	/**
	 * Analyze the url and returns the action of the request.
	 * 
	 * @param url
	 * @return action of the request
	 */
	@Override
	protected String getUrlRequestAction(String url) {
		return "VIEW";
	}

	/**
	 * Determine if the repository requires authentication.
	 * 
	 * @param repository
	 * @return true if authentication required
	 */
	@Override
	protected boolean requiresAuthentication(RepositoryModel repository) {
		return repository.accessRestriction.atLeast(AccessRestrictionType.VIEW);
	}

	/**
	 * Determine if the user can access the repository and perform the specified
	 * action.
	 * 
	 * @param repository
	 * @param user
	 * @param action
	 * @return true if user may execute the action on the repository
	 */
	@Override
	protected boolean canAccess(RepositoryModel repository, UserModel user, String action) {
		return user.canAccessRepository(repository);
	}

}
