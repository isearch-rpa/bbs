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
package org.b3log.symphony.service;

import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.inject.Inject;
import org.b3log.latke.logging.Level;
import org.b3log.latke.logging.Logger;
import org.b3log.latke.model.Pagination;
import org.b3log.latke.repository.CompositeFilter;
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.Filter;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.servlet.HTTPRequestMethod;
import org.b3log.latke.urlfetch.HTTPHeader;
import org.b3log.latke.urlfetch.HTTPRequest;
import org.b3log.latke.urlfetch.HTTPResponse;
import org.b3log.latke.urlfetch.URLFetchService;
import org.b3log.latke.urlfetch.URLFetchServiceFactory;
import org.b3log.latke.util.CollectionUtils;
import org.b3log.latke.util.Paginator;
import org.b3log.latke.util.Stopwatchs;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Tag;
import org.b3log.symphony.repository.ArticleRepository;
import org.b3log.symphony.repository.DomainTagRepository;
import org.b3log.symphony.repository.TagArticleRepository;
import org.b3log.symphony.util.Symphonys;
import org.b3log.symphony.util.URLs;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Search query service.
 * <p>
 * Uses <a href="https://www.elastic.co/products/elasticsearch">Elasticsearch</a> or <a href="https://www.algolia.com">Algolia</a> as the
 * underlying engine.
 * </p>
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @author <a href="http://zephyr.b3log.org">Zephyr</a>
 * @version 1.2.1.2, Aug 23, 2016
 * @since 1.4.0
 */
@Service
public class SearchQueryService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(SearchQueryService.class);

    /**
     * URL fetch service.
     */
    private static final URLFetchService URL_FETCH_SVC = URLFetchServiceFactory.getURLFetchService();

    @Inject
    private ArticleRepository articleRepository;
    
    @Inject
    private DomainTagRepository domainTagRepository;
    
    @Inject
    private TagArticleRepository tagArticleRepository;
    
    /**
     * Searches by Elasticsearch.
     *
     * @param type        the specified document type
     * @param keyword     the specified keyword
     * @param currentPage the specified current page number
     * @param pageSize    the specified page size
     * @return search result, returns {@code null} if not found
     */
    public JSONObject searchElasticsearch(final String type, final String keyword, final int currentPage, final int pageSize,ArrayList<JSONObject> notMustList) {
        final HTTPRequest request = new HTTPRequest();
        request.setRequestMethod(HTTPRequestMethod.POST);
        try {
            request.setURL(new URL(SearchMgmtService.ES_SERVER + "/" + SearchMgmtService.ES_INDEX_NAME + "/" + type
                    + "/_search"));

            final JSONObject reqData = new JSONObject();
            final JSONObject query = new JSONObject();
            final JSONObject bool = new JSONObject();
            query.put("bool", bool);
            final JSONObject must = new JSONObject();
            bool.put("must", must);
            
            //过滤内部帖子
            bool.put("must_not", notMustList);
            
            
            final JSONObject queryString = new JSONObject();
            must.put("query_string", queryString);
            queryString.put("query", keyword );
            //queryString.put("query", "*" + keyword + "*");
            queryString.put("fields", new String[]{Article.ARTICLE_TITLE, Article.ARTICLE_CONTENT});
            queryString.put("default_operator", "and");
            reqData.put("query", query);
            reqData.put("from", (currentPage - 1) * pageSize);
            reqData.put("size", pageSize);
            final JSONArray sort = new JSONArray();
            final JSONObject sortField = new JSONObject();
            sort.put(sortField);
            sortField.put(Article.ARTICLE_CREATE_TIME, "desc");
            sort.put("_score");
            reqData.put("sort", sort);

            final JSONObject highlight = new JSONObject();
            reqData.put("highlight", highlight);
            highlight.put("number_of_fragments", 3);
            highlight.put("fragment_size", 150);
            final JSONObject fields = new JSONObject();
            highlight.put("fields", fields);
            final JSONObject contentField = new JSONObject();
            fields.put(Article.ARTICLE_CONTENT, contentField);
            LOGGER.debug(reqData.toString(4));
            request.setPayload(reqData.toString().getBytes("UTF-8"));
            final HTTPResponse response = URL_FETCH_SVC.fetch(request);

            return new JSONObject(new String(response.getContent(), "UTF-8"));
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Queries failed", e);

            return null;
        }
    }
    
    
    //根领域id获取帖子id
    public Set<String> getarticleIdsByDomainId(String domainId){
    	 Query query = new Query()
                 .addSort(Article.ARTICLE_STICK, SortDirection.DESCENDING);
    	 query.setFilter(makeRecentArticleShowingFilter());
    	 query.addProjection(Keys.OBJECT_ID, String.class);
    	 
    	 final List<String> tagIds = new ArrayList<>();
    	 final Set<String> articleIds = new HashSet<>();
    	 if(domainId!=null&&!"".equals(domainId)){
    		JSONArray domainTags;
			try {
				domainTags = domainTagRepository.getByDomainId(domainId, 1, Integer.MAX_VALUE)
						 .optJSONArray(Keys.RESULTS);
				if (domainTags.length() > 0) {
					for (int i = 0; i < domainTags.length(); i++) {
						tagIds.add(domainTags.optJSONObject(i).optString(Tag.TAG + "_" + Keys.OBJECT_ID));
					}
					Query query1 = new Query().setFilter(
							new PropertyFilter(Tag.TAG + "_" + Keys.OBJECT_ID, FilterOperator.IN, tagIds)).
							addSort(Keys.OBJECT_ID, SortDirection.DESCENDING);
					JSONObject result1 = tagArticleRepository.get(query1);
					final JSONArray tagArticles = result1.optJSONArray(Keys.RESULTS);
					if (tagArticles.length() > 0) {
						for (int i = 0; i < tagArticles.length(); i++) {
							articleIds.add(tagArticles.optJSONObject(i).optString(Article.ARTICLE + "_" + Keys.OBJECT_ID));
						}
					}
				}
			} catch (RepositoryException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	 }
    	 return articleIds;
    }
    
    
    
    
    
    public JSONObject searchJdbc(final String domainId,final String keyword, final int currentPage, final int pageSize) throws Exception {
    	 Query query;
    	 JSONObject result = null;
    	 final JSONObject ret = new JSONObject();
    	 query = makeRecentDefaultQuery(currentPage, pageSize);
    	 
    	 final List<String> tagIds = new ArrayList<>();
    	 final Set<String> articleIds = new HashSet<>();
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
    	 
    	 if (keyword!=null&&!"".equals(keyword)) {
    		 
             final List<Filter> filters = new ArrayList<>();
             filters.add(new PropertyFilter(Article.ARTICLE_TITLE, FilterOperator.LIKE,"%"+keyword+"%"));
             filters.add(new PropertyFilter(Article.ARTICLE_CONTENT, FilterOperator.LIKE, "%"+keyword+"%"));
             filters.add(new PropertyFilter(Article.ARTICLE_TAGS, FilterOperator.LIKE, "%"+keyword+"%"));
             
             
             
             final List<Filter> filters1 = new ArrayList<>();
             if(articleIds!=null&&!articleIds.isEmpty()){
         		for (String str : articleIds) {  
         			filters1.add(new PropertyFilter(Keys.OBJECT_ID, FilterOperator.NOT_EQUAL,str ));
         		} 
         		CompositeFilter typeFilter = new CompositeFilter(CompositeFilterOperator.OR, filters);
         		filters1.add(typeFilter);
         		query.setFilter(new CompositeFilter(CompositeFilterOperator.AND, filters1));
         	 }else{
         		 
         		 query.setFilter(new CompositeFilter(CompositeFilterOperator.OR, filters));
         	 }
             
         }else{
        	 final List<Filter> filters1 = new ArrayList<>();
             if(articleIds!=null&&!articleIds.isEmpty()){
         		for (String str : articleIds) {  
         			filters1.add(new PropertyFilter(Keys.OBJECT_ID, FilterOperator.NOT_EQUAL,str ));
         		} 
         		query.setFilter(new CompositeFilter(CompositeFilterOperator.AND, filters1));
         	 }
         }
         try {
             Stopwatchs.start("Query recent articles");

             result = articleRepository.get(query);
         } catch (final RepositoryException e) {
             LOGGER.log(Level.ERROR, "Gets articles failed", e);

             throw new ServiceException(e);
         } finally {
             Stopwatchs.end();
         }
         
         final int pageCount = result.optJSONObject(Pagination.PAGINATION).optInt(Pagination.PAGINATION_PAGE_COUNT);

         final JSONObject pagination = new JSONObject();
         ret.put(Pagination.PAGINATION, pagination);

         final int windowSize = Symphonys.getInt("latestArticlesWindowSize");

         final List<Integer> pageNums = Paginator.paginate(currentPage, pageSize, pageCount, windowSize);
         pagination.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
         pagination.put(Pagination.PAGINATION_PAGE_NUMS, (Object) pageNums);

         final JSONArray data = result.optJSONArray(Keys.RESULTS);
         final List<JSONObject> articles = CollectionUtils.jsonArrayToList(data);

         //final Integer participantsCnt = Symphonys.getInt("latestArticleParticipantsCnt");
         //genParticipants(articles, participantsCnt);
         ret.put(Article.ARTICLES, (Object) articles);
    	return ret;
    	
    }

    
    private Query makeRecentDefaultQuery(final int currentPageNum, final int fetchSize) {
        final Query ret = new Query()
                .addSort(Article.ARTICLE_STICK, SortDirection.DESCENDING)
                .addSort(Keys.OBJECT_ID, SortDirection.DESCENDING)
                .setPageSize(fetchSize).setCurrentPageNum(currentPageNum);
        ret.setFilter(makeRecentArticleShowingFilter());
        ret.addProjection(Keys.OBJECT_ID, String.class).
                addProjection(Article.ARTICLE_STICK, Long.class).
                addProjection(Article.ARTICLE_CREATE_TIME, Long.class).
                addProjection(Article.ARTICLE_UPDATE_TIME, Long.class).
                addProjection(Article.ARTICLE_LATEST_CMT_TIME, Long.class).
                addProjection(Article.ARTICLE_AUTHOR_ID, String.class).
                addProjection(Article.ARTICLE_TITLE, String.class).
                addProjection(Article.ARTICLE_STATUS, Integer.class).
                addProjection(Article.ARTICLE_VIEW_CNT, Integer.class).
                addProjection(Article.ARTICLE_TYPE, Integer.class).
                addProjection(Article.ARTICLE_PERMALINK, String.class).
                addProjection(Article.ARTICLE_TAGS, String.class).
                addProjection(Article.ARTICLE_LATEST_CMTER_NAME, String.class).
                addProjection(Article.ARTICLE_SYNC_TO_CLIENT, Boolean.class).
                addProjection(Article.ARTICLE_COMMENT_CNT, Integer.class).
                addProjection(Article.ARTICLE_ANONYMOUS, Integer.class).
                addProjection(Article.ARTICLE_PERFECT, Integer.class).
                addProjection(Article.ARTICLE_BAD_CNT, Integer.class).
                addProjection(Article.ARTICLE_GOOD_CNT, Integer.class).
                addProjection(Article.ARTICLE_COLLECT_CNT, Integer.class).
                addProjection(Article.ARTICLE_WATCH_CNT, Integer.class).
                addProjection(Article.ARTICLE_UA, String.class).
                addProjection(Article.ARTICLE_CONTENT, String.class).
                addProjection(Article.ARTICLE_QNA_OFFER_POINT, Integer.class);


        return ret;
    }
    
    private CompositeFilter makeRecentArticleShowingFilter() {
        final List<Filter> filters = new ArrayList<>();
        filters.add(new PropertyFilter(Article.ARTICLE_STATUS, FilterOperator.NOT_EQUAL, Article.ARTICLE_STATUS_C_INVALID));
        filters.add(new PropertyFilter(Article.ARTICLE_TYPE, FilterOperator.NOT_EQUAL, Article.ARTICLE_TYPE_C_DISCUSSION));
        filters.add(new PropertyFilter(Article.ARTICLE_TAGS, FilterOperator.NOT_EQUAL, Tag.TAG_TITLE_C_SANDBOX));
        filters.add(new PropertyFilter(Article.ARTICLE_TAGS, FilterOperator.NOT_LIKE, "B3log%"));
        return new CompositeFilter(CompositeFilterOperator.AND, filters);
    }
    /**
     * Searches by Algolia.
     *
     * @param keyword     the specified keyword
     * @param currentPage the specified current page number
     * @param pageSize    the specified page size
     * @return search result, returns {@code null} if not found
     */
    public JSONObject searchAlgolia(final String keyword, final int currentPage, final int pageSize) {
        final int maxRetries = 3;
        int retries = 1;

        final String appId = Symphonys.get("algolia.appId");
        final String index = Symphonys.get("algolia.index");
        final String key = Symphonys.get("algolia.adminKey");

        while (retries <= maxRetries) {
            String host = appId + "-" + retries + ".algolianet.com";

            try {
                final HTTPRequest request = new HTTPRequest();
                request.addHeader(new HTTPHeader("X-Algolia-API-Key", key));
                request.addHeader(new HTTPHeader("X-Algolia-Application-Id", appId));

                request.setRequestMethod(HTTPRequestMethod.POST);
                request.setURL(new URL("https://" + host + "/1/indexes/" + index + "/query"));

                final JSONObject params = new JSONObject();
                params.put("params", "query=" + URLs.encode(keyword)
                        + "&getRankingInfo=1&facets=*&attributesToRetrieve=*&highlightPreTag=%3Cem%3E"
                        + "&highlightPostTag=%3C%2Fem%3E"
                        + "&facetFilters=%5B%5D&maxValuesPerFacet=100"
                        + "&hitsPerPage=" + pageSize + "&page=" + (currentPage - 1));

                request.setPayload(params.toString().getBytes("UTF-8"));

                final HTTPResponse response = URL_FETCH_SVC.fetch(request);

                final JSONObject ret = new JSONObject(new String(response.getContent(), "UTF-8"));
                if (200 != response.getResponseCode()) {
                    LOGGER.warn(ret.toString(4));

                    return null;
                }

                return ret;
            } catch (final UnknownHostException e) {
                LOGGER.log(Level.ERROR, "Queries failed [UnknownHostException=" + host + "]");

                retries++;

                if (retries > maxRetries) {
                    LOGGER.log(Level.ERROR, "Queries failed [UnknownHostException]");
                }
            } catch (final Exception e) {
                LOGGER.log(Level.ERROR, "Queries failed", e);

                break;
            }
        }

        return null;
    }
}
