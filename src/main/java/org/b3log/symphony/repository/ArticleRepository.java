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
import org.b3log.latke.ioc.LatkeBeanManager;
import org.b3log.latke.ioc.LatkeBeanManagerImpl;
import org.b3log.latke.ioc.inject.Inject;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.util.CollectionUtils;
import org.b3log.symphony.cache.ArticleCache;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Tag;
import org.b3log.symphony.service.ArticleQueryService;
import org.b3log.symphony.service.DomainQueryService;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Article repository.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.1.1.5, Apr 6, 2018
 * @since 0.2.0
 */
@Repository
public class ArticleRepository extends AbstractRepository {

    /**
     * Article cache.
     */
    @Inject
    private ArticleCache articleCache;
    
    /**
     * Public constructor.
     */
    public ArticleRepository() {
        super(Article.ARTICLE);
    }

    @Override
    public void remove(final String id) throws RepositoryException {
        super.remove(id);

        articleCache.removeArticle(id);
    }

    @Override
    public JSONObject get(final String id) throws RepositoryException {
        JSONObject ret = articleCache.getArticle(id);
        if (null != ret) {
            return ret;
        }

        ret = super.get(id);
        if (null == ret) {
            return null;
        }

        articleCache.putArticle(ret);

        return ret;
    }

    @Override
    public void update(final String id, final JSONObject article) throws RepositoryException {
        super.update(id, article);

        article.put(Keys.OBJECT_ID, id);
        articleCache.putArticle(article);
    }

    @Override
    public List<JSONObject> getRandomly(final int fetchSize) throws RepositoryException {
    	final LatkeBeanManager beanManager = LatkeBeanManagerImpl.getInstance();
        final DomainTagRepository domainTagRepository = beanManager.getReference(DomainTagRepository.class);
        final DomainQueryService domainQueryService = beanManager.getReference(DomainQueryService.class);
        final TagArticleRepository tagArticleRepository = beanManager.getReference(TagArticleRepository.class);
        final List<JSONObject> ret = new ArrayList<>();
        final double mid = Math.random();

        String domainId="";
        final List<String> tagIds = new ArrayList<>();
		final Set<String> articleIds = new HashSet<>();
        try {
			final JSONObject domain = domainQueryService.getByURI("内部");
        	if(null!=domain){
        		domainId = domain.optString(Keys.OBJECT_ID);
        	}
		} catch (ServiceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        if(domainId!=null&&!"".equals(domainId)){
			final JSONArray domainTags = domainTagRepository.getByDomainId(domainId, 1, Integer.MAX_VALUE)
					.optJSONArray(Keys.RESULTS);
			if (domainTags.length() > 0) {
				for (int i = 0; i < domainTags.length(); i++) {
					tagIds.add(domainTags.optJSONObject(i).optString(Tag.TAG + "_" + Keys.OBJECT_ID));
				}
				Query query1 = new Query().setFilter(
						new PropertyFilter(Tag.TAG + "_" + Keys.OBJECT_ID, FilterOperator.IN, tagIds)).
						//setCurrentPageNum(currentPageNum).setPageSize(pageSize).
						addSort(Keys.OBJECT_ID, SortDirection.DESCENDING);
				JSONObject result1 = tagArticleRepository.get(query1);
				final JSONArray tagArticles = result1.optJSONArray(Keys.RESULTS);
				if (tagArticles.length() > 0) {
					for (int i = 0; i < tagArticles.length(); i++) {
						articleIds.add(tagArticles.optJSONObject(i).optString(Article.ARTICLE + "_" + Keys.OBJECT_ID));
					}
				}
			}
		}
        
        	
        	
        	final List<Filter> filters1 = new ArrayList<>();
//            filters1.add(new PropertyFilter(Article.ARTICLE_RANDOM_DOUBLE, FilterOperator.GREATER_THAN_OR_EQUAL, 0D));
//            filters1.add(new PropertyFilter(Article.ARTICLE_RANDOM_DOUBLE, FilterOperator.LESS_THAN_OR_EQUAL, mid));
            filters1.add(new PropertyFilter("articleStick", FilterOperator.NOT_EQUAL, 0));
            filters1.add(new PropertyFilter(Article.ARTICLE_STATUS, FilterOperator.NOT_EQUAL, Article.ARTICLE_STATUS_C_INVALID));
            filters1.add(new PropertyFilter(Article.ARTICLE_TYPE, FilterOperator.NOT_EQUAL, Article.ARTICLE_TYPE_C_DISCUSSION));
        	if(articleIds!=null&&!articleIds.isEmpty()){
        		for (String str : articleIds) {  
        			filters1.add(new PropertyFilter(Keys.OBJECT_ID, FilterOperator.NOT_EQUAL,str ));
        		} 
        	}
        	
        	Query query = new Query().setFilter(
            		new CompositeFilter(CompositeFilterOperator.AND, filters1)).addSort("articleStick", SortDirection.DESCENDING).
                    addProjection(Article.ARTICLE_TITLE, String.class).
                    addProjection(Article.ARTICLE_PERMALINK, String.class).
                    addProjection(Article.ARTICLE_AUTHOR_ID, String.class).
                    setCurrentPageNum(1).setPageSize(Symphonys.getInt("sideRandomArticlesCnt")).setPageCount(1);
            final JSONObject result2 = get(query);
            final JSONArray array2 = result2.optJSONArray(Keys.RESULTS);
            final List<JSONObject> list2 = CollectionUtils.jsonArrayToList(array2);

            ret.addAll(list2);

        return ret;
    }
    
    
    
    
//    public List<JSONObject> getRandomly(final int fetchSize) throws RepositoryException {
//    	final LatkeBeanManager beanManager = LatkeBeanManagerImpl.getInstance();
//    	final DomainTagRepository domainTagRepository = beanManager.getReference(DomainTagRepository.class);
//    	final DomainQueryService domainQueryService = beanManager.getReference(DomainQueryService.class);
//    	final TagArticleRepository tagArticleRepository = beanManager.getReference(TagArticleRepository.class);
//    	final List<JSONObject> ret = new ArrayList<>();
//    	final double mid = Math.random();
//    	
//    	String domainId="";
//    	final List<String> tagIds = new ArrayList<>();
//    	final Set<String> articleIds = new HashSet<>();
//    	try {
//    		final JSONObject domain = domainQueryService.getByURI("内部");
//    		if(null!=domain){
//    			domainId = domain.optString(Keys.OBJECT_ID);
//    		}
//    	} catch (ServiceException e) {
//    		// TODO Auto-generated catch block
//    		e.printStackTrace();
//    	}
//    	
//    	if(domainId!=null&&!"".equals(domainId)){
//    		final JSONArray domainTags = domainTagRepository.getByDomainId(domainId, 1, Integer.MAX_VALUE)
//    				.optJSONArray(Keys.RESULTS);
//    		if (domainTags.length() > 0) {
//    			for (int i = 0; i < domainTags.length(); i++) {
//    				tagIds.add(domainTags.optJSONObject(i).optString(Tag.TAG + "_" + Keys.OBJECT_ID));
//    			}
//    			Query query1 = new Query().setFilter(
//    					new PropertyFilter(Tag.TAG + "_" + Keys.OBJECT_ID, FilterOperator.IN, tagIds)).
//    					//setCurrentPageNum(currentPageNum).setPageSize(pageSize).
//    					addSort(Keys.OBJECT_ID, SortDirection.DESCENDING);
//    			JSONObject result1 = tagArticleRepository.get(query1);
//    			final JSONArray tagArticles = result1.optJSONArray(Keys.RESULTS);
//    			if (tagArticles.length() > 0) {
//    				for (int i = 0; i < tagArticles.length(); i++) {
//    					articleIds.add(tagArticles.optJSONObject(i).optString(Article.ARTICLE + "_" + Keys.OBJECT_ID));
//    				}
//    			}
//    		}
//    	}
//    	
//    	
//    	final List<Filter> filters = new ArrayList<>();
//    	filters.add(new PropertyFilter(Article.ARTICLE_RANDOM_DOUBLE, FilterOperator.GREATER_THAN_OR_EQUAL, mid));
//    	filters.add(new PropertyFilter(Article.ARTICLE_RANDOM_DOUBLE, FilterOperator.LESS_THAN_OR_EQUAL, mid));
//    	filters.add(new PropertyFilter(Article.ARTICLE_STATUS, FilterOperator.NOT_EQUAL, Article.ARTICLE_STATUS_C_INVALID));
//    	filters.add(new PropertyFilter(Article.ARTICLE_TYPE, FilterOperator.NOT_EQUAL, Article.ARTICLE_TYPE_C_DISCUSSION));
//    	if(articleIds!=null&&!articleIds.isEmpty()){
//    		for (String str : articleIds) {  
//    			filters.add(new PropertyFilter(Keys.OBJECT_ID, FilterOperator.NOT_EQUAL,str ));
//    		} 
//    	}
//    	
//    	Query query = new Query().setFilter(
//    			new CompositeFilter(CompositeFilterOperator.AND, filters)).
//    			addProjection(Article.ARTICLE_TITLE, String.class).
//    			addProjection(Article.ARTICLE_PERMALINK, String.class).
//    			addProjection(Article.ARTICLE_AUTHOR_ID, String.class).
//    			setCurrentPageNum(1).setPageSize(fetchSize).setPageCount(1);
//    	final JSONObject result1 = get(query);
//    	final JSONArray array1 = result1.optJSONArray(Keys.RESULTS);
//    	
//    	final List<JSONObject> list1 = CollectionUtils.jsonArrayToList(array1);
//    	ret.addAll(list1);
//    	
//    	final int reminingSize = fetchSize - array1.length();
//    	if (0 != reminingSize) { // Query for remains
//    		
//    		
//    		final List<Filter> filters1 = new ArrayList<>();
//          filters1.add(new PropertyFilter(Article.ARTICLE_RANDOM_DOUBLE, FilterOperator.GREATER_THAN_OR_EQUAL, 0D));
//          filters1.add(new PropertyFilter(Article.ARTICLE_RANDOM_DOUBLE, FilterOperator.LESS_THAN_OR_EQUAL, mid));
//    		filters1.add(new PropertyFilter("articleStick", FilterOperator.NOT_EQUAL, ""));
//    		filters1.add(new PropertyFilter(Article.ARTICLE_STATUS, FilterOperator.NOT_EQUAL, Article.ARTICLE_STATUS_C_INVALID));
//    		filters1.add(new PropertyFilter(Article.ARTICLE_TYPE, FilterOperator.NOT_EQUAL, Article.ARTICLE_TYPE_C_DISCUSSION));
//    		if(articleIds!=null&&!articleIds.isEmpty()){
//    			for (String str : articleIds) {  
//    				filters1.add(new PropertyFilter(Keys.OBJECT_ID, FilterOperator.NOT_EQUAL,str ));
//    			} 
//    		}
//    		
//    		query = new Query().setFilter(
//    				new CompositeFilter(CompositeFilterOperator.AND, filters1)).
//    				addProjection(Article.ARTICLE_TITLE, String.class).
//    				addProjection(Article.ARTICLE_PERMALINK, String.class).
//    				addProjection(Article.ARTICLE_AUTHOR_ID, String.class).
//    				setCurrentPageNum(1).setPageSize(reminingSize).setPageCount(1);
//    		final JSONObject result2 = get(query);
//    		final JSONArray array2 = result2.optJSONArray(Keys.RESULTS);
//    		final List<JSONObject> list2 = CollectionUtils.jsonArrayToList(array2);
//    		
//    		ret.addAll(list2);
//    	}
//    	
//    	return ret;
//    }

    /**
     * Gets an article by the specified article title.
     *
     * @param articleTitle the specified article title
     * @return an article, {@code null} if not found
     * @throws RepositoryException repository exception
     */
    public JSONObject getByTitle(final String articleTitle) throws RepositoryException {
        final Query query = new Query().setFilter(new PropertyFilter(Article.ARTICLE_TITLE,
                FilterOperator.EQUAL, articleTitle)).setPageCount(1);

        final JSONObject result = get(query);
        final JSONArray array = result.optJSONArray(Keys.RESULTS);
        if (0 == array.length()) {
            return null;
        }

        return array.optJSONObject(0);
    }
}