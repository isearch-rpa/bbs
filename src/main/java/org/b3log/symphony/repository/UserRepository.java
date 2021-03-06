/*
 * Symphony - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Copyright (C) 2012-2018, b3log.org & hacpai.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.symphony.repository;

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.inject.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.latke.util.CollectionUtils;
import org.b3log.symphony.cache.UserCache;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Role;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * User repository.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.1.2.0, Oct 2, 2017
 * @since 0.2.0
 */
@Repository
public class UserRepository extends AbstractRepository {

    /**
     * User cache.
     */
    @Inject
    private UserCache userCache;

    /**
     * Public constructor.
     */
    public UserRepository() {
        super(User.USER);
    }

    @Override
    public JSONObject get(final String id) throws RepositoryException {
        JSONObject ret = userCache.getUser(id);
        if (null != ret) {
            return ret;
        }

        ret = super.get(id);

        if (null == ret) {
            return null;
        }

        userCache.putUser(ret);

        return ret;
    }

    @Override
    public void update(final String id, final JSONObject user) throws RepositoryException {
        final JSONObject old = get(id);
        if (null == old) {
            return;
        }

        userCache.RemoveUser(old);
        super.update(id, user);
        user.put(Keys.OBJECT_ID, id);
        userCache.putUser(user);
    }

    /**
     * Gets a user by the specified name.
     *
     * @param name the specified name
     * @return user, returns {@code null} if not found
     * @throws RepositoryException repository exception
     */
    public JSONObject getByName(final String name) throws RepositoryException {
        JSONObject ret = userCache.getUserByName(name);
        if (null != ret) {
            return ret;
        }

        final Query query = new Query().setPageCount(1);
        query.setFilter(new PropertyFilter(User.USER_NAME, FilterOperator.EQUAL, name));

        final JSONObject result = get(query);
        final JSONArray array = result.optJSONArray(Keys.RESULTS);

        if (0 == array.length()) {
            return null;
        }

        ret = array.optJSONObject(0);

        userCache.putUser(ret);

        return ret;
    }
    

    /**
     * Gets a user by the specified email.
     *
     * @param email the specified email
     * @return user, returns {@code null} if not found
     * @throws RepositoryException repository exception
     */
    public JSONObject getByEmail(final String email) throws RepositoryException {
        final Query query = new Query().setPageCount(1);
        query.setFilter(new PropertyFilter(User.USER_EMAIL, FilterOperator.EQUAL, email.toLowerCase().trim()));

        final JSONObject result = get(query);
        final JSONArray array = result.optJSONArray(Keys.RESULTS);

        if (0 == array.length()) {
            return null;
        }

        return array.optJSONObject(0);
    }
    public JSONObject getByCode(final String code) throws RepositoryException {
    	final Query query = new Query().setPageCount(1);
    	query.setFilter(new PropertyFilter("code", FilterOperator.EQUAL, code.trim()));
    	
    	final JSONObject result = get(query);
    	final JSONArray array = result.optJSONArray(Keys.RESULTS);
    	
    	if (0 == array.length()) {
    		return null;
    	}
    	
    	return array.optJSONObject(0);
    }
    
    public JSONObject getByCodeAndUserName(final String userName,final String code) throws RepositoryException {
    	final Query query = new Query().setPageCount(1);
    	
    	final List<Filter> filters = new ArrayList<>();
        filters.add(new PropertyFilter("userName", FilterOperator.EQUAL,userName));
        filters.add(new PropertyFilter("code", FilterOperator.EQUAL, code));
        query.setFilter(new CompositeFilter(CompositeFilterOperator.AND, filters));
    	
    	
    	final JSONObject result = get(query);
    	final JSONArray array = result.optJSONArray(Keys.RESULTS);
    	
    	if (0 == array.length()) {
    		return null;
    	}
    	
    	return array.optJSONObject(0);
    }
    
    public JSONObject getByRoleAndEmail(final String userRole,final String userEmail) throws RepositoryException {
    	final Query query = new Query().setPageCount(1);
    	
    	final List<Filter> filters = new ArrayList<>();
    	filters.add(new PropertyFilter("userRole", FilterOperator.EQUAL,userRole));
    	filters.add(new PropertyFilter("userEmail", FilterOperator.EQUAL, userEmail));
    	query.setFilter(new CompositeFilter(CompositeFilterOperator.AND, filters));
    	
    	
    	final JSONObject result = get(query);
    	final JSONArray array = result.optJSONArray(Keys.RESULTS);
    	
    	if (0 == array.length()) {
    		return null;
    	}
    	
    	return array.optJSONObject(0);
    }
    
    
    
    public JSONObject getByRoleAndUserName(final String userRole,final String userName) throws RepositoryException {
    	final Query query = new Query().setPageCount(1);
    	
    	final List<Filter> filters = new ArrayList<>();
    	filters.add(new PropertyFilter("userRole", FilterOperator.EQUAL,userRole));
    	filters.add(new PropertyFilter("userName", FilterOperator.EQUAL, userName));
    	query.setFilter(new CompositeFilter(CompositeFilterOperator.AND, filters));
    	
    	
    	final JSONObject result = get(query);
    	final JSONArray array = result.optJSONArray(Keys.RESULTS);
    	
    	if (0 == array.length()) {
    		return null;
    	}
    	
    	return array.optJSONObject(0);
    }
    
    
    
    public JSONObject getByDDId(final String ddId) throws RepositoryException {
        final Query query = new Query().setPageCount(1);
        query.setFilter(new PropertyFilter("ddId", FilterOperator.EQUAL, ddId));

        final JSONObject result = get(query);
        final JSONArray array = result.optJSONArray(Keys.RESULTS);

        if (0 == array.length()) {
            return null;
        }

        return array.optJSONObject(0);
    }

    /**
     * Gets the administrators.
     *
     * @return administrators, returns an empty list if not found or error
     * @throws RepositoryException repository exception
     */
    public List<JSONObject> getAdmins() throws RepositoryException {
        final Query query = new Query().setFilter(
                new PropertyFilter(User.USER_ROLE, FilterOperator.EQUAL, Role.ROLE_ID_C_ADMIN)).setPageCount(1)
                .addSort(Keys.OBJECT_ID, SortDirection.ASCENDING);
        final JSONObject result = get(query);
        final JSONArray array = result.optJSONArray(Keys.RESULTS);

        return CollectionUtils.<JSONObject>jsonArrayToList(array);
    }

    /**
     * Determine whether the specified email is administrator's.
     *
     * @param email the specified email
     * @return {@code true} if it is administrator's email, {@code false} otherwise
     * @throws RepositoryException repository exception
     */
    public boolean isAdminEmail(final String email) throws RepositoryException {
        final JSONObject user = getByEmail(email);

        if (null == user) {
            return false;
        }

        return Role.ROLE_ID_C_ADMIN.equals(user.optString(User.USER_ROLE));
    }
}
