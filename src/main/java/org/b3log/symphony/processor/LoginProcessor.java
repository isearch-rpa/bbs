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
package org.b3log.symphony.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.ioc.inject.Inject;
import org.b3log.latke.logging.Level;
import org.b3log.latke.logging.Logger;
import org.b3log.latke.model.User;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.servlet.HTTPRequestContext;
import org.b3log.latke.servlet.HTTPRequestMethod;
import org.b3log.latke.servlet.annotation.After;
import org.b3log.latke.servlet.annotation.Before;
import org.b3log.latke.servlet.annotation.RequestProcessing;
import org.b3log.latke.servlet.annotation.RequestProcessor;
import org.b3log.latke.servlet.renderer.freemarker.AbstractFreeMarkerRenderer;
import org.b3log.latke.util.Locales;
import org.b3log.latke.util.MD5;
import org.b3log.latke.util.Requests;
import org.b3log.latke.util.Strings;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Invitecode;
import org.b3log.symphony.model.Notification;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.Tag;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.model.Verifycode;
import org.b3log.symphony.processor.advice.AnonymousViewCheck;
import org.b3log.symphony.processor.advice.CSRFToken;
import org.b3log.symphony.processor.advice.LoginCheck;
import org.b3log.symphony.processor.advice.PermissionGrant;
import org.b3log.symphony.processor.advice.stopwatch.StopwatchEndAdvice;
import org.b3log.symphony.processor.advice.stopwatch.StopwatchStartAdvice;
import org.b3log.symphony.processor.advice.validate.UserForgetPwdValidation;
import org.b3log.symphony.processor.advice.validate.UserRegister2Validation;
import org.b3log.symphony.processor.advice.validate.UserRegisterValidation;
import org.b3log.symphony.service.DataModelService;
import org.b3log.symphony.service.InvitecodeMgmtService;
import org.b3log.symphony.service.InvitecodeQueryService;
import org.b3log.symphony.service.NotificationMgmtService;
import org.b3log.symphony.service.OptionQueryService;
import org.b3log.symphony.service.PointtransferMgmtService;
import org.b3log.symphony.service.RoleQueryService;
import org.b3log.symphony.service.TagQueryService;
import org.b3log.symphony.service.UserMgmtService;
import org.b3log.symphony.service.UserQueryService;
import org.b3log.symphony.service.VerifycodeMgmtService;
import org.b3log.symphony.service.VerifycodeQueryService;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiGettokenRequest;
import com.dingtalk.api.request.OapiSnsGetPersistentCodeRequest;
import com.dingtalk.api.request.OapiSnsGetSnsTokenRequest;
import com.dingtalk.api.request.OapiSnsGettokenRequest;
import com.dingtalk.api.request.OapiSnsGetuserinfoRequest;
import com.dingtalk.api.request.OapiUserGetRequest;
import com.dingtalk.api.request.OapiUserGetUseridByUnionidRequest;
import com.dingtalk.api.response.OapiGettokenResponse;
import com.dingtalk.api.response.OapiSnsGetPersistentCodeResponse;
import com.dingtalk.api.response.OapiSnsGetSnsTokenResponse;
import com.dingtalk.api.response.OapiSnsGettokenResponse;
import com.dingtalk.api.response.OapiSnsGetuserinfoResponse;
import com.dingtalk.api.response.OapiUserGetResponse;
import com.dingtalk.api.response.OapiUserGetUseridByUnionidResponse;
import com.qiniu.util.Auth;
import com.taobao.api.ApiException;
import sun.misc.BASE64Decoder;
//import com.taobao.api.ApiException;
/**
 * Login/Register processor.
 * <ul>
 * <li>Registration (/register), GET/POST</li>
 * <li>Login (/login), GET/POST</li>
 * <li>Logout (/logout), GET</li>
 * <li>Reset password (/reset-pwd), GET/POST</li>
 * </ul>
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @author <a href="http://vanessa.b3log.org">Liyuan Li</a>
 * @version 1.13.12.2, May 31, 2018
 * @since 0.2.0
 */
@RequestProcessor
public class LoginProcessor {

    /**
     * Wrong password tries.
     * <p>
     * &lt;userId, {"wrongCount": int, "captcha": ""}&gt;
     * </p>
     */
    public static final Map<String, JSONObject> WRONG_PWD_TRIES = new ConcurrentHashMap<>();

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(LoginProcessor.class);

    /**
     * User management service.
     */
    @Inject
    private UserMgmtService userMgmtService;

    /**
     * User query service.
     */
    @Inject
    private UserQueryService userQueryService;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Pointtransfer management service.
     */
    @Inject
    private PointtransferMgmtService pointtransferMgmtService;

    /**
     * Data model service.
     */
    @Inject
    private DataModelService dataModelService;

    /**
     * Verifycode management service.
     */
    @Inject
    private VerifycodeMgmtService verifycodeMgmtService;

    /**
     * Verifycode query service.
     */
    @Inject
    private VerifycodeQueryService verifycodeQueryService;

    /**
     * Option query service.
     */
    @Inject
    private OptionQueryService optionQueryService;

    /**
     * Invitecode query service.
     */
    @Inject
    private InvitecodeQueryService invitecodeQueryService;

    /**
     * Invitecode management service.
     */
    @Inject
    private InvitecodeMgmtService invitecodeMgmtService;

    /**
     * Invitecode management service.
     */
    @Inject
    private NotificationMgmtService notificationMgmtService;

    /**
     * Role query service.
     */
    @Inject
    private RoleQueryService roleQueryService;

    /**
     * Tag query service.
     */
    @Inject
    private TagQueryService tagQueryService;

    /**
     * Next guide step.
     *
     * @param context  the specified context
     * @param request  the specified request
     * @param response the specified response
     */
    @RequestProcessing(value = "/guide/next", method = HTTPRequestMethod.POST)
    @Before(adviceClass = {LoginCheck.class})
    public void nextGuideStep(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response) {
        context.renderJSON();

        JSONObject requestJSONObject;
        try {
            requestJSONObject = Requests.parseRequestJSONObject(request, response);
        } catch (final Exception e) {
            LOGGER.warn(e.getMessage());

            return;
        }

        JSONObject user = (JSONObject) request.getAttribute(User.USER);
        final String userId = user.optString(Keys.OBJECT_ID);

        int step = requestJSONObject.optInt(UserExt.USER_GUIDE_STEP);

        if (UserExt.USER_GUIDE_STEP_STAR_PROJECT < step || UserExt.USER_GUIDE_STEP_FIN >= step) {
            step = UserExt.USER_GUIDE_STEP_FIN;
        }

        try {
            user = userQueryService.getUser(userId);
            user.put(UserExt.USER_GUIDE_STEP, step);
            userMgmtService.updateUser(userId, user);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Guide next step [" + step + "] failed", e);

            return;
        }

        context.renderJSON(true);
    }

    /**
     * Shows guide page.
     *
     * @param context  the specified context
     * @param request  the specified request
     * @param response the specified response
     * @throws Exception exception
     */
    @RequestProcessing(value = "/guide", method = HTTPRequestMethod.GET)
    @Before(adviceClass = {StopwatchStartAdvice.class, LoginCheck.class})
    @After(adviceClass = {CSRFToken.class, PermissionGrant.class, StopwatchEndAdvice.class})
    public void showGuide(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
            throws Exception {
        final JSONObject currentUser = (JSONObject) request.getAttribute(User.USER);
        final int step = currentUser.optInt(UserExt.USER_GUIDE_STEP);
        if (UserExt.USER_GUIDE_STEP_FIN == step) {
            response.sendRedirect(Latkes.getServePath());

            return;
        }

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        renderer.setTemplateName("/verify/guide.ftl");

        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModel.put(Common.CURRENT_USER, currentUser);

        final List<JSONObject> tags = tagQueryService.getTags(32);
        dataModel.put(Tag.TAGS, tags);

        final List<JSONObject> users = userQueryService.getNiceUsers(6);
        final Iterator<JSONObject> iterator = users.iterator();
        while (iterator.hasNext()) {
            final JSONObject user = iterator.next();
            if (user.optString(Keys.OBJECT_ID).equals(currentUser.optString(Keys.OBJECT_ID))) {
                iterator.remove();

                break;
            }
        }
        dataModel.put(User.USERS, users);

        // Qiniu file upload authenticate
        final Auth auth = Auth.create(Symphonys.get("qiniu.accessKey"), Symphonys.get("qiniu.secretKey"));
        final String uploadToken = auth.uploadToken(Symphonys.get("qiniu.bucket"));
        dataModel.put("qiniuUploadToken", uploadToken);
        dataModel.put("qiniuDomain", Symphonys.get("qiniu.domain"));

        if (!Symphonys.getBoolean("qiniu.enabled")) {
            dataModel.put("qiniuUploadToken", "");
        }

        final long imgMaxSize = Symphonys.getLong("upload.img.maxSize");
        dataModel.put("imgMaxSize", imgMaxSize);
        final long fileMaxSize = Symphonys.getLong("upload.file.maxSize");
        dataModel.put("fileMaxSize", fileMaxSize);

        dataModelService.fillHeaderAndFooter(request, response, dataModel);
    }

    /**
     * Shows login page.
     *
     * @param context  the specified context
     * @param request  the specified request
     * @param response the specified response
     * @throws Exception exception
     */
    @RequestProcessing(value = "/login", method = HTTPRequestMethod.GET)
    @Before(adviceClass = StopwatchStartAdvice.class)
    @After(adviceClass = {PermissionGrant.class, StopwatchEndAdvice.class})
    public void showLogin(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
            throws Exception {
        if (null != userQueryService.getCurrentUser(request)
                || userMgmtService.tryLogInWithCookie(request, response)) {
            response.sendRedirect(Latkes.getServePath());

            return;
        }

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);

        String referer = request.getParameter(Common.GOTO);
        if(referer.endsWith("showDDLogin")){
        	referer= referer.substring(0,referer.length() - 11);
        }
        if (StringUtils.isBlank(referer)) {
            referer = request.getHeader("referer");
        }

        if (!StringUtils.startsWith(referer, Latkes.getServePath())) {
            referer = Latkes.getServePath();
        }

        renderer.setTemplateName("/verify/login.ftl");

        final Map<String, Object> dataModel = renderer.getDataModel();
        if(referer.endsWith(Latkes.getServePath()+"/")){
        	
        	dataModel.put(Common.GOTO, referer+"domain/经验分享");
        }else{
        	
        	dataModel.put(Common.GOTO, referer);
        }

        dataModelService.fillHeaderAndFooter(request, response, dataModel);
    }

    /**
     * Shows forget password page.
     *
     * @param context  the specified context
     * @param request  the specified request
     * @param response the specified response
     * @throws Exception exception
     */
    @RequestProcessing(value = "/forget-pwd", method = HTTPRequestMethod.GET)
    @Before(adviceClass = StopwatchStartAdvice.class)
    @After(adviceClass = {PermissionGrant.class, StopwatchEndAdvice.class})
    public void showForgetPwd(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
            throws Exception {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        final Map<String, Object> dataModel = renderer.getDataModel();

        renderer.setTemplateName("verify/forget-pwd.ftl");

        dataModelService.fillHeaderAndFooter(request, response, dataModel);
    }
    
   

    /**
     * Forget password.
     *
     * @param context  the specified context
     * @param request  the specified request
     * @param response the specified response
     * @throws Exception exception
     */
    @RequestProcessing(value = "/forget-pwd", method = HTTPRequestMethod.POST)
    @Before(adviceClass = UserForgetPwdValidation.class)
    public void forgetPwd(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
            throws Exception {
        context.renderJSON();

        final JSONObject requestJSONObject = (JSONObject) request.getAttribute(Keys.REQUEST);
        final String email = requestJSONObject.optString(User.USER_EMAIL);

        try {
            final JSONObject user = userQueryService.getUserByEmail(email);
            if (null == user) {
                context.renderFalseResult().renderMsg(langPropsService.get("notFoundUserLabel"));

                return;
            }

            final String userId = user.optString(Keys.OBJECT_ID);

            final JSONObject verifycode = new JSONObject();
            verifycode.put(Verifycode.BIZ_TYPE, Verifycode.BIZ_TYPE_C_RESET_PWD);
            final String code = RandomStringUtils.randomAlphanumeric(6);
            verifycode.put(Verifycode.CODE, code);
            verifycode.put(Verifycode.EXPIRED, DateUtils.addDays(new Date(), 1).getTime());
            verifycode.put(Verifycode.RECEIVER, email);
            verifycode.put(Verifycode.STATUS, 1);
//            verifycode.put(Verifycode.STATUS, Verifycode.STATUS_C_UNSENT);
            verifycode.put(Verifycode.TYPE, Verifycode.TYPE_C_EMAIL);
            verifycode.put(Verifycode.USER_ID, userId);
            verifycodeMgmtService.addVerifycode(verifycode);

            context.renderTrueResult().renderMsg(code);
//            context.renderTrueResult().renderMsg(langPropsService.get("verifycodeSentLabel"));
        } catch (final ServiceException e) {
            final String msg = langPropsService.get("resetPwdLabel") + " - " + e.getMessage();
            LOGGER.log(Level.ERROR, msg + "[email=" + email + "]");

            context.renderMsg(msg);
        }
    }

    /**
     * Shows reset password page.
     *
     * @param context  the specified context
     * @param request  the specified request
     * @param response the specified response
     * @throws Exception exception
     */
    @RequestProcessing(value = "/reset-pwd", method = HTTPRequestMethod.GET)
    @Before(adviceClass = StopwatchStartAdvice.class)
    @After(adviceClass = {PermissionGrant.class, StopwatchEndAdvice.class})
    public void showResetPwd(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
            throws Exception {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        final Map<String, Object> dataModel = renderer.getDataModel();

        final String code = request.getParameter("code");
        final JSONObject verifycode = verifycodeQueryService.getVerifycode(code);
        if (null == verifycode) {
            dataModel.put(Keys.MSG, langPropsService.get("verifycodeExpiredLabel"));
            renderer.setTemplateName("/error/custom.ftl");
        } else {
            renderer.setTemplateName("verify/reset-pwd.ftl");

            final String userId = verifycode.optString(Verifycode.USER_ID);
            final JSONObject user = userQueryService.getUser(userId);
            dataModel.put(User.USER, user);
            dataModel.put(Common.CODE, code);
        }

        dataModelService.fillHeaderAndFooter(request, response, dataModel);
    }

    /**
     * Resets password.
     *
     * @param context  the specified context
     * @param request  the specified request
     * @param response the specified response
     * @throws ServletException servlet exception
     * @throws IOException      io exception
     */
    @RequestProcessing(value = "/reset-pwd", method = HTTPRequestMethod.POST)
    public void resetPwd(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        context.renderJSON();

        final JSONObject requestJSONObject = Requests.parseRequestJSONObject(request, response);
        final String password = requestJSONObject.optString(User.USER_PASSWORD); // Hashed
        final String userId = requestJSONObject.optString(Common.USER_ID);
        final String code = requestJSONObject.optString(Common.CODE);
        final JSONObject verifycode = verifycodeQueryService.getVerifycode(code);
        if (null == verifycode || !verifycode.optString(Verifycode.USER_ID).equals(userId)) {
            context.renderMsg(langPropsService.get("verifycodeExpiredLabel"));

            return;
        }

        String name = null;
        String email = null;
        try {
            final JSONObject user = userQueryService.getUser(userId);
            if (null == user) {
                context.renderMsg(langPropsService.get("resetPwdLabel") + " - " + "User Not Found");

                return;
            }

            user.put(User.USER_PASSWORD, password);
            userMgmtService.updatePassword(user);
            verifycodeMgmtService.removeByCode(code);
            context.renderTrueResult();
            LOGGER.info("User [email=" + user.optString(User.USER_EMAIL) + "] reseted password");

            Sessions.login(request, response, user, true);
        } catch (final ServiceException e) {
            final String msg = langPropsService.get("resetPwdLabel") + " - " + e.getMessage();
            LOGGER.log(Level.ERROR, msg + "[name={0}, email={1}]", name, email);

            context.renderMsg(msg);
        }
    }

    /**
     * Shows registration page.
     *
     * @param context  the specified context
     * @param request  the specified request
     * @param response the specified response
     * @throws Exception exception
     */
    @RequestProcessing(value = "/register", method = HTTPRequestMethod.GET)
    @Before(adviceClass = StopwatchStartAdvice.class)
    @After(adviceClass = {PermissionGrant.class, StopwatchEndAdvice.class})
    public void showRegister(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
            throws Exception {
        if (null != userQueryService.getCurrentUser(request)
                || userMgmtService.tryLogInWithCookie(request, response)) {
            response.sendRedirect(Latkes.getServePath());

            return;
        }

        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);

        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModel.put(Common.REFERRAL, "");

        boolean useInvitationLink = false;

        String referral = request.getParameter("r");
        if (!UserRegisterValidation.invalidUserName(referral)) {
            final JSONObject referralUser = userQueryService.getUserByName(referral);
            if (null != referralUser) {
                dataModel.put(Common.REFERRAL, referral);

                final Map<String, JSONObject> permissions =
                        roleQueryService.getUserPermissionsGrantMap(referralUser.optString(Keys.OBJECT_ID));
                final JSONObject useILPermission =
                        permissions.get(Permission.PERMISSION_ID_C_COMMON_USE_INVITATION_LINK);
                useInvitationLink = useILPermission.optBoolean(Permission.PERMISSION_T_GRANT);
            }
        }

        final String code = request.getParameter("code");
        if (Strings.isEmptyOrNull(code)) { // Register Step 1
            renderer.setTemplateName("verify/register.ftl");
        } else { // Register Step 2
            final JSONObject verifycode = verifycodeQueryService.getVerifycode(code);
            if (null == verifycode) {
                dataModel.put(Keys.MSG, langPropsService.get("verifycodeExpiredLabel"));
                renderer.setTemplateName("/error/custom.ftl");
            } else {
                renderer.setTemplateName("verify/register2.ftl");

                final String userId = verifycode.optString(Verifycode.USER_ID);
                final JSONObject user = userQueryService.getUser(userId);
                dataModel.put(User.USER, user);

                if (UserExt.USER_STATUS_C_VALID == user.optInt(UserExt.USER_STATUS)
                        || UserExt.NULL_USER_NAME.equals(user.optString(User.USER_NAME))) {
                    dataModel.put(Keys.MSG, langPropsService.get("userExistLabel"));
                    renderer.setTemplateName("/error/custom.ftl");
                } else {
                    referral = StringUtils.substringAfter(code, "r=");
                    if (!Strings.isEmptyOrNull(referral)) {
                        dataModel.put(Common.REFERRAL, referral);
                    }
                }
            }
        }

        final String allowRegister = optionQueryService.getAllowRegister();
        dataModel.put(Option.ID_C_MISC_ALLOW_REGISTER, allowRegister);
        if (useInvitationLink && "2".equals(allowRegister)) {
            dataModel.put(Option.ID_C_MISC_ALLOW_REGISTER, "1");
        }

        dataModelService.fillHeaderAndFooter(request, response, dataModel);
    }

    /**
     * Register Step 1.
     *
     * @param context  the specified context
     * @param request  the specified request
     * @param response the specified response
     * @throws ServletException servlet exception
     * @throws IOException      io exception
     */
    @RequestProcessing(value = "/register", method = HTTPRequestMethod.POST)
    @Before(adviceClass = UserRegisterValidation.class)
    public void register(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        context.renderJSON();

        final JSONObject requestJSONObject = (JSONObject) request.getAttribute(Keys.REQUEST);
        final String name = requestJSONObject.optString(User.USER_NAME);
        final String email = requestJSONObject.optString(User.USER_EMAIL);
        final String invitecode = requestJSONObject.optString(Invitecode.INVITECODE);
        final String referral = requestJSONObject.optString(Common.REFERRAL);

        final JSONObject user = new JSONObject();
        user.put(User.USER_NAME, name);
        user.put(User.USER_EMAIL, email);
        user.put(User.USER_PASSWORD, "");
        final Locale locale = Locales.getLocale();
        user.put(UserExt.USER_LANGUAGE, locale.getLanguage() + "_" + locale.getCountry());

        try {
            final String newUserId = userMgmtService.addUser(user);

            final JSONObject verifycode = new JSONObject();
            verifycode.put(Verifycode.BIZ_TYPE, Verifycode.BIZ_TYPE_C_REGISTER);
            String code = RandomStringUtils.randomAlphanumeric(6);
            if (!Strings.isEmptyOrNull(referral)) {
                code += "r=" + referral;
            }
            verifycode.put(Verifycode.CODE, code);
            verifycode.put(Verifycode.EXPIRED, DateUtils.addDays(new Date(), 1).getTime());
            verifycode.put(Verifycode.RECEIVER, email);
            verifycode.put(Verifycode.STATUS, Verifycode.STATUS_C_UNSENT);
            verifycode.put(Verifycode.TYPE, Verifycode.TYPE_C_EMAIL);
            verifycode.put(Verifycode.USER_ID, newUserId);
            verifycodeMgmtService.addVerifycode(verifycode);

            final String allowRegister = optionQueryService.getAllowRegister();
            if ("2".equals(allowRegister) && StringUtils.isNotBlank(invitecode)) {
                final JSONObject ic = invitecodeQueryService.getInvitecode(invitecode);
                ic.put(Invitecode.USER_ID, newUserId);
                ic.put(Invitecode.USE_TIME, System.currentTimeMillis());
                final String icId = ic.optString(Keys.OBJECT_ID);

                invitecodeMgmtService.updateInvitecode(icId, ic);
            }

            context.renderTrueResult().renderMsg(langPropsService.get("verifycodeSentLabel"));
        } catch (final ServiceException e) {
            final String msg = langPropsService.get("registerFailLabel") + " - " + e.getMessage();
            LOGGER.log(Level.ERROR, msg + "[name={0}, email={1}]", name, email);

            context.renderMsg(msg);
        }
    }

    /**
     * Register Step 2.
     *
     * @param context  the specified context
     * @param request  the specified request
     * @param response the specified response
     * @throws ServletException servlet exception
     * @throws IOException      io exception
     */
    @RequestProcessing(value = "/register2", method = HTTPRequestMethod.POST)
    @Before(adviceClass = UserRegister2Validation.class)
    public void register2(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        context.renderJSON();

        final JSONObject requestJSONObject = (JSONObject) request.getAttribute(Keys.REQUEST);

        final String password = requestJSONObject.optString(User.USER_PASSWORD); // Hashed
        final int appRole = requestJSONObject.optInt(UserExt.USER_APP_ROLE);
        final String referral = requestJSONObject.optString(Common.REFERRAL);
        final String userId = requestJSONObject.optString(Common.USER_ID);

        String name = null;
        String email = null;
        try {
            final JSONObject user = userQueryService.getUser(userId);
            if (null == user) {
                context.renderMsg(langPropsService.get("registerFailLabel") + " - " + "User Not Found");

                return;
            }

            name = user.optString(User.USER_NAME);
            email = user.optString(User.USER_EMAIL);

            user.put(UserExt.USER_APP_ROLE, appRole);
            user.put(User.USER_PASSWORD, password);
            user.put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_VALID);

            userMgmtService.addUser(user);

            Sessions.login(request, response, user, false);

            final String ip = Requests.getRemoteAddr(request);
            userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), ip, true);

            if (!Strings.isEmptyOrNull(referral) && !UserRegisterValidation.invalidUserName(referral)) {
                final JSONObject referralUser = userQueryService.getUserByName(referral);
                if (null != referralUser) {
                    final String referralId = referralUser.optString(Keys.OBJECT_ID);
                    // Point
                    pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, userId,
                            Pointtransfer.TRANSFER_TYPE_C_INVITED_REGISTER,
                            Pointtransfer.TRANSFER_SUM_C_INVITE_REGISTER, referralId, System.currentTimeMillis());
                    pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, referralId,
                            Pointtransfer.TRANSFER_TYPE_C_INVITE_REGISTER,
                            Pointtransfer.TRANSFER_SUM_C_INVITE_REGISTER, userId, System.currentTimeMillis());

                    final JSONObject notification = new JSONObject();
                    notification.put(Notification.NOTIFICATION_USER_ID, referralId);
                    notification.put(Notification.NOTIFICATION_DATA_ID, userId);

                    notificationMgmtService.addInvitationLinkUsedNotification(notification);
                }
            }

            final JSONObject ic = invitecodeQueryService.getInvitecodeByUserId(userId);
            if (null != ic && Invitecode.STATUS_C_UNUSED == ic.optInt(Invitecode.STATUS)) {
                ic.put(Invitecode.STATUS, Invitecode.STATUS_C_USED);
                ic.put(Invitecode.USER_ID, userId);
                ic.put(Invitecode.USE_TIME, System.currentTimeMillis());
                final String icId = ic.optString(Keys.OBJECT_ID);

                invitecodeMgmtService.updateInvitecode(icId, ic);

                final String icGeneratorId = ic.optString(Invitecode.GENERATOR_ID);
                if (StringUtils.isNotBlank(icGeneratorId) && !Pointtransfer.ID_C_SYS.equals(icGeneratorId)) {
                    pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, icGeneratorId,
                            Pointtransfer.TRANSFER_TYPE_C_INVITECODE_USED,
                            Pointtransfer.TRANSFER_SUM_C_INVITECODE_USED, userId, System.currentTimeMillis());

                    final JSONObject notification = new JSONObject();
                    notification.put(Notification.NOTIFICATION_USER_ID, icGeneratorId);
                    notification.put(Notification.NOTIFICATION_DATA_ID, userId);

                    notificationMgmtService.addInvitecodeUsedNotification(notification);
                }
            }

            context.renderTrueResult();

            LOGGER.log(Level.INFO, "Registered a user [name={0}, email={1}]", name, email);
        } catch (final ServiceException e) {
            final String msg = langPropsService.get("registerFailLabel") + " - " + e.getMessage();
            LOGGER.log(Level.ERROR, msg + "[name={0}, email={1}]", name, email);

            context.renderMsg(msg);
        }
    }

    /**
     * Logins user.
     *
     * @param context  the specified context
     * @param request  the specified request
     * @param response the specified response
     * @throws ServletException servlet exception
     * @throws IOException      io exception
     */
    @RequestProcessing(value = "/login", method = HTTPRequestMethod.POST)
    public void login(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        context.renderJSON().renderMsg(langPropsService.get("loginFailLabel"));

        final JSONObject requestJSONObject = Requests.parseRequestJSONObject(request, response);
        final String nameOrEmail = requestJSONObject.optString("nameOrEmail");

        try {
            JSONObject user = userQueryService.getUserByName(nameOrEmail);
            if (null == user) {
                user = userQueryService.getUserByEmail(nameOrEmail);
            }

            if (null == user) {
                context.renderMsg(langPropsService.get("notFoundUserLabel"));

                return;
            }

            if (UserExt.USER_STATUS_C_INVALID == user.optInt(UserExt.USER_STATUS)) {
                userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), "", false);
                context.renderMsg(langPropsService.get("userBlockLabel"));

                return;
            }

            if (UserExt.USER_STATUS_C_NOT_VERIFIED == user.optInt(UserExt.USER_STATUS)) {
                userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), "", false);
                context.renderMsg(langPropsService.get("notVerifiedLabel"));

                return;
            }

            if (UserExt.USER_STATUS_C_INVALID_LOGIN == user.optInt(UserExt.USER_STATUS)) {
                userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), "", false);
                context.renderMsg(langPropsService.get("invalidLoginLabel"));

                return;
            }

            final String userId = user.optString(Keys.OBJECT_ID);
            JSONObject wrong = WRONG_PWD_TRIES.get(userId);
            if (null == wrong) {
                wrong = new JSONObject();
            }

            final int wrongCount = wrong.optInt(Common.WRON_COUNT);
            if (wrongCount > 3) {
                final String captcha = requestJSONObject.optString(CaptchaProcessor.CAPTCHA);
                if (!StringUtils.equals(wrong.optString(CaptchaProcessor.CAPTCHA), captcha)) {
                    context.renderMsg(langPropsService.get("captchaErrorLabel"));
                    context.renderJSONValue(Common.NEED_CAPTCHA, userId);

                    return;
                }
            }

            final String userPassword = user.optString(User.USER_PASSWORD);
            if (userPassword.equals(requestJSONObject.optString(User.USER_PASSWORD))) {
                final String token = Sessions.login(request, response, user, requestJSONObject.optBoolean(Common.REMEMBER_LOGIN));

                final String ip = Requests.getRemoteAddr(request);
                userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), ip, true);

                context.renderMsg("").renderTrueResult();
                context.renderJSONValue(Keys.TOKEN, token);

                WRONG_PWD_TRIES.remove(userId);
                return;
            }

            if (wrongCount > 2) {
                context.renderJSONValue(Common.NEED_CAPTCHA, userId);
            }

            wrong.put(Common.WRON_COUNT, wrongCount + 1);
            WRONG_PWD_TRIES.put(userId, wrong);

            context.renderMsg(langPropsService.get("wrongPwdLabel"));
        } catch (final Exception e) {
            context.renderMsg(langPropsService.get("loginFailLabel"));
        }
    }
    
    
    /**
     * Rpa登录接口
     * @param context
     * @param request
     * @param response
     * @throws IOException
     */
    @RequestProcessing(value = "/symLogin", method = HTTPRequestMethod.GET)
    public void symLogin(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
    		throws IOException {
    	context.renderJSON().renderMsg(langPropsService.get("loginFailLabel"));
    	
    	final JSONObject requestJSONObject = Requests.parseRequestJSONObject(request, response);
    	final String cname = "guest";
    	//final String cname = request.getParameter("cname").trim();
    	//final String dcode = request.getParameter("dcode").trim();
    	
    	try {
    		//JSONObject user = userQueryService.getUserByCode(dcode);
    		JSONObject user = userQueryService.getUserByName(cname);
    		//JSONObject user = userQueryService.getByCodeAndUserName(cname, dcode);
    		if (user == null) {

    	        String userId;
    	        try {
    	            user = new JSONObject();
    	            user.put(User.USER_NAME, cname);
    	            //user.put("code", dcode);
    	            user.put(User.USER_EMAIL, Symphonys.get("userEmail"));
    	            user.put(User.USER_PASSWORD, MD5.hash(Symphonys.get("userPassWord")));
    	            user.put("userRole", Symphonys.get("userRole"));
    	            user.put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_VALID);
    	            user.put("userGuideStep", 0);
    	            user.put("userPoint", Symphonys.getInt("pointInit"));
    	            final Locale locale = Locales.getLocale();
    	            user.put(UserExt.USER_LANGUAGE, locale.getLanguage() + "_" + locale.getCountry());

    	            userId = userMgmtService.symAddUser(user);
    	            //user = userQueryService.getByCodeAndUserName(cname, dcode);
    	            user = userQueryService.getUserByName(cname);
    	        } catch (final ServiceException e) {
//    	            final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
//    	            context.setRenderer(renderer);
//    	            renderer.setTemplateName("admin/error.ftl");
    	        	final String msg = langPropsService.get("registerFailLabel") + " - " + e.getMessage();

    	            context.renderMsg(msg);
    	            return;
    	        }
    			
    		}
    		
    		
    		if (UserExt.USER_STATUS_C_INVALID == user.optInt(UserExt.USER_STATUS)) {
    			userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), "", false);
    			context.renderMsg(langPropsService.get("userBlockLabel"));
    			
    			return;
    		}
    		
    		if (UserExt.USER_STATUS_C_NOT_VERIFIED == user.optInt(UserExt.USER_STATUS)) {
    			userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), "", false);
    			context.renderMsg(langPropsService.get("notVerifiedLabel"));
    			
    			return;
    		}
    		
    		if (UserExt.USER_STATUS_C_INVALID_LOGIN == user.optInt(UserExt.USER_STATUS)) {
    			userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), "", false);
    			context.renderMsg(langPropsService.get("invalidLoginLabel"));
    			
    			return;
    		}
    		
    		final String userId = user.optString(Keys.OBJECT_ID);
			final String token = Sessions.login(request, response, user, requestJSONObject.optBoolean(Common.REMEMBER_LOGIN));
			
			final String ip = Requests.getRemoteAddr(request);
			userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), ip, true);
			
			context.renderMsg("").renderTrueResult();
			context.renderJSONValue(Keys.TOKEN, token);
			response.sendRedirect(Latkes.getServePath()+"/recent");
			//WRONG_PWD_TRIES.remove(userId);
    		
    	} catch (final ServiceException e) {
    		context.renderMsg(langPropsService.get("loginFailLabel"));
    	}
    }
    
    
    /**
     * 钉钉扫码登录接口
     * @param context
     * @param request
     * @param response
     * @throws IOException
     */
    @RequestProcessing(value = "/dingLogin", method = HTTPRequestMethod.GET)
    public void dingLogin(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
    		throws IOException {
    	final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        renderer.setTemplateName("/verify/dingdingLogin.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
    	String access_token="";
    	String openid="";
    	String persistent_code="";
    	String sns_token="";
    	String userInfo="";
    	String unionid="";
    	String nick="";
    	String ddUserInfo="";
    	String ddUserId="";
    	String result="";
    	JSONObject user=null;
    	final JSONObject requestJSONObject = Requests.parseRequestJSONObject(request, response);

    	try {
    		String tmp_auth_code=request.getParameter("code");
    		String corpid=Symphonys.get("corpid");
    		String corpsecret=Symphonys.get("corpsecret");
    		access_token = gettoken();
    		String jsonPersistentCode =getPersistentCode(access_token,tmp_auth_code);
    		JSONObject prsistentCodeJo = new JSONObject(jsonPersistentCode);
    		String prsistentCodeErrcode=prsistentCodeJo.getString("errcode");
			if("0".equals(prsistentCodeErrcode)){
				LOGGER.info("*****************用户扫码登录开始*****************");
				openid=prsistentCodeJo.getString("openid");
				persistent_code=prsistentCodeJo.getString("persistent_code");
				
				sns_token=getSnsToken(access_token,openid,persistent_code);
				if(!"".equals(sns_token)){
					
					String userInfoJson=getuserinfo(sns_token);
					JSONObject userInfoJsonJo = new JSONObject(userInfoJson);
					String userInfoErrcode=userInfoJsonJo.getString("errcode");
					if("0".equals(userInfoErrcode)){
						userInfo=userInfoJsonJo.getString("user_info");
						unionid=new JSONObject(userInfo).getString("unionid");
						nick=new JSONObject(userInfo).getString("nick");
						user = userQueryService.getByDDId(unionid);
						
						if(user==null&&!"".equals(unionid)){
							access_token=getDdToken(corpid,corpsecret);
							if(access_token==null||"".equals(access_token)){
								result="系统异常！";
				    			dataModel.put("result",result);
				    			LOGGER.info("*****************用户扫码登录结束， 结果：扫码登录失败*****************");
				                dataModelService.fillHeaderAndFooter(request, response, dataModel);
				                return;
							}
							ddUserId=getUseridByUnionid(access_token,unionid);
							if(!"".equals(ddUserId)){
								ddUserInfo=getUserByUserId(access_token,ddUserId);
							}
						}
					}
				}
			}else{
				dataModel.put("result","");
				dataModelService.fillHeaderAndFooter(request, response, dataModel);
                return;
			}
			if(unionid==null||"".equals(unionid)){
				result="系统异常！";
    			dataModel.put("result",result);
    			LOGGER.info("*****************用户扫码登录结束， 结果：扫码登录失败*****************");
                dataModelService.fillHeaderAndFooter(request, response, dataModel);
                return;
			}
    		
    		//用户在公司钉钉通讯录中
    		if(user==null&&!"".equals(ddUserInfo)&& !"".equals(nick)){
				JSONObject ddUserInfoJsonJo = new JSONObject(ddUserInfo);
				String email=ddUserInfoJsonJo.getString("email");
				String userNickname=ddUserInfoJsonJo.getString("name");
				String orgEmail=ddUserInfoJsonJo.has("orgEmail")?ddUserInfoJsonJo.getString("orgEmail"):"";
				if((email==null||"".equals(email))&&(orgEmail==null||"".equals(orgEmail))){
					result="登录失败！";
	    			dataModel.put("result",result);
	    			LOGGER.info("*****************用户扫码登录结束， 结果：扫码登录失败(email或orgEmail为空)*****************");
	                dataModelService.fillHeaderAndFooter(request, response, dataModel);
	                return;
				}
				
				if((email==null || "".equals(email))){
					if(!"".equals(orgEmail)){
						user = userQueryService.getUserByEmail(orgEmail);
					}
				}else{
					user = userQueryService.getUserByEmail(email);
					if(user==null && !"".equals(orgEmail)){
						user = userQueryService.getUserByEmail(orgEmail);
					}
				}
				if(user!=null){
					user.put("ddId", unionid);
					userMgmtService.updateDDId(user.optString(Keys.OBJECT_ID), user);
					user=userQueryService.getUser(user.optString("oId"));
				}else{
					orgEmail="".equals(orgEmail)?email:orgEmail;
					if(!"".equals(orgEmail)){
						String userName=orgEmail.substring(0,orgEmail.indexOf("@"));
						if(userQueryService.getUserByName(userName)!=null){
							result="您的默认用户名("+userName+") 已存在，请联系管理员！";
							LOGGER.info("*****************用户扫码登录结束， 结果：扫码登录失败("+result+")*****************");
			    			dataModel.put("result",result);
			                dataModelService.fillHeaderAndFooter(request, response, dataModel);
			                return;
						}
						//user = userQueryService.getUserByName(nick);
						user = new JSONObject();
	    				user.put(User.USER_NAME, userName);
	    				user.put(UserExt.USER_NICKNAME, userNickname);
	    				user.put("ddId", unionid);
	    				user.put(User.USER_EMAIL, orgEmail);
	    				user.put(User.USER_PASSWORD, MD5.hash(Symphonys.get("userPassWord")));
	    				user.put("userRole", Symphonys.get("staffRole"));
	    				user.put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_VALID);
	    				user.put("userGuideStep", 0);
	    				user.put("userPoint", Symphonys.getInt("pointInit"));
	    				final Locale locale = Locales.getLocale();
	    				user.put(UserExt.USER_LANGUAGE, locale.getLanguage() + "_" + locale.getCountry());
	    				String userId = userMgmtService.symAddUser(user);
	    				user = userQueryService.getUser(userId);
					}
					
					
					
//					result="您的账号未绑定钉钉，请使用账号和密码登录后进入个人主页进行绑定！";
//					LOGGER.info("*****************用户扫码登录结束， 结果：扫码登录失败*****************");
//	    			dataModel.put("result",result);
//	                dataModelService.fillHeaderAndFooter(request, response, dataModel);
//	                return;
				}
//				if(user ==null){
//					//user = userQueryService.getUserByName(nick);
//					user = new JSONObject();
//    				user.put(User.USER_NAME, nick);
//    				user.put(User.USER_EMAIL, email);
//    				user.put(User.USER_PASSWORD, MD5.hash(Symphonys.get("userPassWord")));
//    				user.put("userRole", Symphonys.get("staffRole"));
//    				user.put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_VALID);
//    				user.put("userGuideStep", 0);
//    				user.put("userPoint", Symphonys.getInt("pointInit"));
//    				final Locale locale = Locales.getLocale();
//    				user.put(UserExt.USER_LANGUAGE, locale.getLanguage() + "_" + locale.getCountry());
//    				String userId = userMgmtService.symAddUser(user);
//    				user = userQueryService.getUser(userId);
//					
//				}
    		}
    		//不在钉钉通讯录中的用户
    		if(user==null&&"".equals(ddUserInfo)&& !"".equals(nick)){
    			
    			result="您的账号未绑定钉钉，请使用账号和密码登录后进入个人主页进行绑定！";
    			LOGGER.info("*****************用户扫码登录结束， 结果：扫码登录失败（"+result+"）*****************");
    			dataModel.put("result",result);
                dataModelService.fillHeaderAndFooter(request, response, dataModel);
                return;
//    			user = userQueryService.getByRoleAndUserName(Symphonys.get("userRole"),nick);
//    			if(user==null){
//    				user = new JSONObject();
//    				user.put(User.USER_NAME, nick);
//    				//user.put(User.USER_EMAIL, email);
//    				user.put(User.USER_PASSWORD, MD5.hash(Symphonys.get("userPassWord")));
//    				user.put("userRole", Symphonys.get("userRole"));
//    				user.put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_VALID);
//    				user.put("userGuideStep", 0);
//    				user.put("userPoint", Symphonys.getInt("pointInit"));
//    				final Locale locale = Locales.getLocale();
//    				user.put(UserExt.USER_LANGUAGE, locale.getLanguage() + "_" + locale.getCountry());
//    				String userId = userMgmtService.symAddUser(user);
//    				user = userQueryService.getUser(userId);
//    			}
    		}
    		
			if (UserExt.USER_STATUS_C_INVALID == user.optInt(UserExt.USER_STATUS)) {
				userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), "", false);
				context.renderMsg(langPropsService.get("userBlockLabel"));
				
				return;
			}
			
			if (UserExt.USER_STATUS_C_NOT_VERIFIED == user.optInt(UserExt.USER_STATUS)) {
				userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), "", false);
				context.renderMsg(langPropsService.get("notVerifiedLabel"));
				
				return;
			}
			
			if (UserExt.USER_STATUS_C_INVALID_LOGIN == user.optInt(UserExt.USER_STATUS)) {
				userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), "", false);
				context.renderMsg(langPropsService.get("invalidLoginLabel"));
				
				return;
			}
			
			final String userId = user.optString(Keys.OBJECT_ID);
			final String token = Sessions.login(request, response, user, requestJSONObject.optBoolean(Common.REMEMBER_LOGIN));
			
			final String ip = Requests.getRemoteAddr(request);
			userMgmtService.updateOnlineStatus(user.optString(Keys.OBJECT_ID), ip, true);
			
			context.renderMsg("").renderTrueResult();
			context.renderJSONValue(Keys.TOKEN, token);
        	LOGGER.info("*****************用户扫码登录结束：用户id="+user.optString("oId") +"用户名="+user.optString("userName")+" 结果：扫码登录成功*****************");

			response.sendRedirect(Latkes.getServePath()+"/domain/"+URLEncoder.encode("经验分享", "UTF-8"));
    		
    		//WRONG_PWD_TRIES.remove(userId);
    		
    	} catch (final Exception e) {
        	LOGGER.info("*****************用户扫码登录异常，异常信息："+e);

    		result="登录失败！";
			dataModel.put("result",result);
            try {
				dataModelService.fillHeaderAndFooter(request, response, dataModel);
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
    	}
    	
    }
    
    
    
    
    /**
     * 绑定钉钉页面
     * @param context
     * @param request
     * @param response
     * @throws Exception
     */
    @RequestProcessing(value = "/showBindDD", method = HTTPRequestMethod.GET)
    @Before(adviceClass = {StopwatchStartAdvice.class, AnonymousViewCheck.class})
    @After(adviceClass = {PermissionGrant.class, StopwatchEndAdvice.class})
    public void showBindDD(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
    		throws Exception {
    	final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
    	context.setRenderer(renderer);
    	final Map<String, Object> dataModel = renderer.getDataModel();
    	renderer.setTemplateName("verify/bindDD.ftl");
		dataModel.put("result", "");
    	dataModelService.fillHeaderAndFooter(request, response, dataModel);
    }
    
    
    /**
     * 绑定钉钉
     * @param context
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    @RequestProcessing(value = "/bindDD", method = HTTPRequestMethod.GET)
    @Before(adviceClass = {StopwatchStartAdvice.class, AnonymousViewCheck.class})
    @After(adviceClass = {PermissionGrant.class, StopwatchEndAdvice.class})
    public void bindDD(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
            throws Exception, IOException {
    	final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
        context.setRenderer(renderer);
        renderer.setTemplateName("/verify/bindDD.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        String result="";
        String access_token="";
    	String openid="";
    	String persistent_code="";
    	String sns_token="";
    	String userInfo="";
    	String unionid="";
        final JSONObject user = (JSONObject) request.getAttribute(User.USER);

        if(user!=null){
        	try{
        		String tmp_auth_code=request.getParameter("code");
//        		String corpid=Symphonys.get("corpid");
//        		String corpsecret=Symphonys.get("corpsecret");
        		access_token = gettoken();
        		String jsonPersistentCode =getPersistentCode(access_token,tmp_auth_code);
        		JSONObject prsistentCodeJo = new JSONObject(jsonPersistentCode);
        		String prsistentCodeErrcode=prsistentCodeJo.getString("errcode");
        		if("0".equals(prsistentCodeErrcode)){
        			LOGGER.info("*****************用户绑定钉钉开始：用户id="+user.optString("oId")+"*****************" );
        			openid=prsistentCodeJo.getString("openid");
        			persistent_code=prsistentCodeJo.getString("persistent_code");
        			
        			sns_token=getSnsToken(access_token,openid,persistent_code);
        			if(!"".equals(sns_token)){
        				
        				String userInfoJson=getuserinfo(sns_token);
        				JSONObject userInfoJsonJo = new JSONObject(userInfoJson);
        				String userInfoErrcode=userInfoJsonJo.getString("errcode");
        				if("0".equals(userInfoErrcode)){
        					userInfo=userInfoJsonJo.getString("user_info");
        					unionid=new JSONObject(userInfo).getString("unionid");
        					if(!"".equals(unionid)){
        				        user.put("ddId", unionid);
        				        String userId=user.optString("oId");
    				            userMgmtService.updateDDId(userId, user);
    				            result="绑定成功！";
//    				            dataModelService.fillHeaderAndFooter(request, response, dataModel);
//    				            return;
//    				            response.sendRedirect(Latkes.getServePath()+"/showBindDD?result=1");
        					}else{
        						result="绑定失败！";
        					}
        				}else{
        					result="绑定失败！";
        				}
        			}else{
        				result="绑定失败！";
        			}
        		}else{
        			dataModel.put("result","");
                    dataModelService.fillHeaderAndFooter(request, response, dataModel);
        		}
        	}catch (Exception e) {
        		result="绑定失败！";
            	LOGGER.info("*****************用户绑定钉钉异常：用户id="+user.optString("oId") +"用户名="+user.optString("userName")+" 异常信息：" +e);

			}
        	LOGGER.info("*****************用户绑定钉钉结束：用户id="+user.optString("oId") +"用户名="+user.optString("userName")+" 结果：" +result+"*****************");
        	dataModel.put("result",result);
            dataModelService.fillHeaderAndFooter(request, response, dataModel);
        }
    }
    
    
    /**
     * 钉钉扫描登录页面
     * @param context
     * @param request
     * @param response
     * @throws Exception
     */
    @RequestProcessing(value = "/showDDLogin", method = HTTPRequestMethod.GET)
    @Before(adviceClass = StopwatchStartAdvice.class)
    @After(adviceClass = {PermissionGrant.class, StopwatchEndAdvice.class})
    public void showDDLogin(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
    		throws Exception {
    	final AbstractFreeMarkerRenderer renderer = new SkinRenderer(request);
    	context.setRenderer(renderer);
    	final Map<String, Object> dataModel = renderer.getDataModel();
    	dataModel.put("result","");
    	renderer.setTemplateName("verify/dingdingLogin.ftl");
    	
    	dataModelService.fillHeaderAndFooter(request, response, dataModel);
    }
    
    
    
    public String getuserinfo(String sns_token){
    	String jsonStr="";
    	try {
    		LOGGER.info("***************调用钉钉getuserinfo接口开始（获取用户授权的个人信息  https://oapi.dingtalk.com/sns/getuserinfo）参数：{sns_token="+sns_token+"}");

    		DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/sns/getuserinfo");
    		OapiSnsGetuserinfoRequest request = new OapiSnsGetuserinfoRequest();
    		request.setSnsToken(sns_token);
    		request.setHttpMethod("GET");
    		OapiSnsGetuserinfoResponse response = client.execute(request);
    		jsonStr=response.getBody();
    		LOGGER.info("***************调用钉钉getuserinfo接口结束（获取用户授权的个人信息  https://oapi.dingtalk.com/sns/getuserinfo）返回结果："+jsonStr);

		} catch (Exception e) {
			// TODO: handle exception
    		LOGGER.info("***************调用钉钉getuserinfo接口异常（获取用户授权的个人信息  https://oapi.dingtalk.com/sns/getuserinfo）参数：{sns_token="+sns_token+"} 异常信息："+e);

		}
    	return jsonStr;
    }
    
    
    
    public String gettoken(){
    	String access_token="";
    	try {
    		LOGGER.info("***************调用钉钉gettoken接口开始（获取钉钉开放应用的ACCESS_TOKEN  https://oapi.dingtalk.com/sns/gettoken）参数：{appid="+Symphonys.get("appid")+";appsecret="+Symphonys.get("appsecret")+"}");
    		DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/sns/gettoken");
    		OapiSnsGettokenRequest request = new OapiSnsGettokenRequest();
    		request.setAppid(Symphonys.get("appid"));
    		request.setAppsecret(Symphonys.get("appsecret"));
    		request.setHttpMethod("GET");
			OapiSnsGettokenResponse response = client.execute(request);
			String jsonStr=response.getBody();
			JSONObject jo = new JSONObject(jsonStr);
			String errcode=jo.getString("errcode");
			if("0".equals(errcode)){
				access_token=jo.getString("access_token");
			}
    		LOGGER.info("***************调用钉钉gettoken接口结束（获取钉钉开放应用的ACCESS_TOKEN  https://oapi.dingtalk.com/sns/gettoken）返回结果："+jsonStr);

		} catch (Exception e) {
    		LOGGER.info("***************调用钉钉gettoken接口异常（获取钉钉开放应用的ACCESS_TOKEN  https://oapi.dingtalk.com/sns/gettoken）参数：{appid="+Symphonys.get("appid")+";appsecret="+Symphonys.get("appsecret")+"}  异常信息："+e);

			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return access_token;
    }
    
    
    /**
     * 获取用户授权的持久授权码
     * @param accessToken
     * @return
     */
    public String getPersistentCode(String accessToken,String tmp_auth_code){
    	String jsonStr="";
    	DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/sns/get_persistent_code");
    	OapiSnsGetPersistentCodeRequest request = new OapiSnsGetPersistentCodeRequest();
    	request.setTmpAuthCode(tmp_auth_code);
    	try {
    		LOGGER.info("***************调用钉钉getPersistentCode接口开始（获取用户授权的持久授权码  https://oapi.dingtalk.com/sns/get_persistent_code）参数：{accessToken="+accessToken+";tmp_auth_code="+tmp_auth_code+"}");

			OapiSnsGetPersistentCodeResponse response = client.execute(request,accessToken);
			jsonStr=response.getBody();
    		LOGGER.info("***************调用钉钉getPersistentCode接口结束（获取用户授权的持久授权码 https://oapi.dingtalk.com/sns/get_persistent_code）返回结果："+jsonStr);

		} catch (ApiException e) {
			// TODO Auto-generated catch block
    		LOGGER.info("***************调用钉钉getPersistentCode接口异常（获取用户授权的持久授权码 https://oapi.dingtalk.com/sns/get_persistent_code）参数：{accessToken="+accessToken+";tmp_auth_code="+tmp_auth_code+"} 异常信息："+e);

			e.printStackTrace();
		}
    	return jsonStr;
    }
    
    
    
    /**
     * 获取用户授权的SNS_TOKEN
     * @param accessToken
     * @param openid
     * @param persistent_code
     * @return
     */
    public String getSnsToken(String accessToken,String openid,String persistent_code){
    	String sns_token="";
    	try {
    		LOGGER.info("***************调用钉钉getSnsToken接口开始（获取用户授权的SNS_TOKEN https://oapi.dingtalk.com/sns/get_sns_token）参数：{accessToken="+accessToken+";openid="+openid+";persistent_code="+persistent_code+"}");
    		DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/sns/get_sns_token");
    		OapiSnsGetSnsTokenRequest request = new OapiSnsGetSnsTokenRequest();
    		request.setOpenid(openid);
    		request.setPersistentCode(persistent_code);
			OapiSnsGetSnsTokenResponse response = client.execute(request,accessToken);
			String jsonStr=response.getBody();
			JSONObject jo = new JSONObject(jsonStr);
			String errcode=jo.getString("errcode");
			if("0".equals(errcode)){
				sns_token=jo.getString("sns_token");
			}
    		LOGGER.info("***************调用钉钉getSnsToken接口开始（获取用户授权的SNS_TOKEN https://oapi.dingtalk.com/sns/get_sns_token）返回结果："+jsonStr);

		} catch (Exception e) {
			// TODO Auto-generated catch block
    		LOGGER.info("***************调用钉钉getSnsToken接口异常（获取用户授权的SNS_TOKEN https://oapi.dingtalk.com/sns/get_sns_token）参数：{accessToken="+accessToken+";openid="+openid+";persistent_code="+persistent_code+"}  异常信息："+e);

			e.printStackTrace();
		}
    	return sns_token;
    }
    
    
    /**
     * 根据unionid获取userid
     * @param accessToken
     * @param unionid
     * @return
     */
    public String getUseridByUnionid(String accessToken,String unionid){
    	String userId="";
    	try {
    		LOGGER.info("***************调用钉钉getUseridByUnionid接口开始（根据unionid获取userid https://oapi.dingtalk.com/user/getUseridByUnionid）参数：{accessToken="+accessToken+";unionid="+unionid+"}");

    		DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/user/getUseridByUnionid");
    		OapiUserGetUseridByUnionidRequest request = new OapiUserGetUseridByUnionidRequest();
    		request.setUnionid(unionid);
    		request.setHttpMethod("GET");
			OapiUserGetUseridByUnionidResponse response = client.execute(request, accessToken);
			String jsonStr=response.getBody();
			JSONObject jo = new JSONObject(jsonStr);
			String errcode=jo.getString("errcode");
			if("0".equals(errcode)){
				userId=jo.getString("userid");
			}
    		LOGGER.info("***************调用钉钉getUseridByUnionid接口结束（根据unionid获取userid https://oapi.dingtalk.com/user/getUseridByUnionid）返回结果："+jsonStr);

		} catch (Exception e) {
			// TODO Auto-generated catch block
    		LOGGER.info("***************调用钉钉getUseridByUnionid接口异常（根据unionid获取userid https://oapi.dingtalk.com/user/getUseridByUnionid）参数：{accessToken="+accessToken+";unionid="+unionid+"} 异常信息："+e);

			e.printStackTrace();
		}
    	return userId;
    }

    
    
    public  String getDdToken(String corpid,String corpsecret){
		String token="";
		try {
    		LOGGER.info("***************调用钉钉getDdToken接口开始（获取钉钉开放应用的ACCESS_TOKEN https://oapi.dingtalk.com/gettoken）参数：{corpid="+corpid+";corpsecret="+corpsecret+"}");
			DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/gettoken");
			OapiGettokenRequest request = new OapiGettokenRequest();
			request.setCorpid(corpid);
			request.setCorpsecret(corpsecret);
			request.setHttpMethod("GET");
			OapiGettokenResponse response;
			response = client.execute(request);
			String jsonStr=response.getBody();
			System.out.println(jsonStr);
			JSONObject jo = new JSONObject(jsonStr);
			String errcode=jo.getString("errcode");
			if("0".equals(errcode)){
				token=jo.getString("access_token");
				System.out.println(token);
			}
    		LOGGER.info("***************调用钉钉getDdToken接口结束（获取钉钉开放应用的ACCESS_TOKEN https://oapi.dingtalk.com/gettoken）返回结果："+jsonStr);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
    		LOGGER.info("***************调用钉钉getDdToken接口异常（获取钉钉开放应用的ACCESS_TOKEN https://oapi.dingtalk.com/gettoken）参数：{corpid="+corpid+";corpsecret="+corpsecret+"} 异常信息："+e);

		}
		return token;
	}
    
    
    /**
     * 根据userid 获取用户详情
     * @param access_token
     * @param userid
     * @return
     */
    public String getUserByUserId(String access_token,String userid){
    	String userInfo="";
    	String jsonStr="";
    	try {
    		LOGGER.info("***************调用钉钉getUserByUserId接口开始（根据userid 获取用户详情 https://oapi.dingtalk.com/user/get）参数：{access_token="+access_token+";userId="+userid);
	    	DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/user/get");
	    	OapiUserGetRequest request = new OapiUserGetRequest();
	    	request.setUserid(userid);
	    	request.setHttpMethod("GET");
			OapiUserGetResponse response = client.execute(request, access_token);
			jsonStr=response.getBody();
			JSONObject jo = new JSONObject(jsonStr);
			String errcode=jo.getString("errcode");
			if("0".equals(errcode)){
				userInfo=jsonStr;
			}
    		LOGGER.info("***************调用钉钉getUserByUserId接口结束（根据userid 获取用户详情 https://oapi.dingtalk.com/user/get）返回结果："+jsonStr);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			LOGGER.info("***************调用钉钉getUserByUserId接口异常（根据userid 获取用户详情 https://oapi.dingtalk.com/user/get）参数：{access_token="+access_token+";userId="+userid+"} 异常信息："+e);
		}
    	return userInfo;
    }
    /**
     * Logout.
     *
     * @param context the specified context
     * @throws IOException io exception
     */
    @RequestProcessing(value = {"/logout"}, method = HTTPRequestMethod.GET)
    public void logout(final HTTPRequestContext context) throws IOException {
        final HttpServletRequest httpServletRequest = context.getRequest();

        Sessions.logout(httpServletRequest, context.getResponse());

        String destinationURL = httpServletRequest.getParameter(Common.GOTO);
        if (!StringUtils.startsWith(destinationURL, Latkes.getServePath())) {
            destinationURL = "/";
        }

        context.getResponse().sendRedirect(destinationURL);
    }

    /**
     * Expires invitecodes.
     *
     * @param request  the specified HTTP servlet request
     * @param response the specified HTTP servlet response
     * @param context  the specified HTTP request context
     * @throws Exception exception
     */
    @RequestProcessing(value = "/cron/invitecode-expire", method = HTTPRequestMethod.GET)
    @Before(adviceClass = StopwatchStartAdvice.class)
    @After(adviceClass = StopwatchEndAdvice.class)
    public void expireInvitecodes(final HttpServletRequest request, final HttpServletResponse response, final HTTPRequestContext context)
            throws Exception {
        final String key = Symphonys.get("keyOfSymphony");
        if (!key.equals(request.getParameter("key"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);

            return;
        }

        invitecodeMgmtService.expireInvitecodes();

        context.renderJSON().renderTrueResult();
    }
    
    

    /**
     * 根据邮箱获取用户密码，如用户不存在自动开通
     * @param context
     * @param request
     * @param response
     * @throws IOException
     */
    @RequestProcessing(value = "/checkRpaUser", method = HTTPRequestMethod.GET)
    public void checkRpaUser(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
    		throws IOException {
    	
    	String email = request.getParameter("email");
    	String signCode=request.getParameter("signCode");
    	String userName=request.getParameter("userName");
    	String companyName=request.getParameter("companyName");
    	JSONObject user;
    	String status="1";//账号已开通
    	String passWord="";
		LOGGER.info("***************调用checkRpaUser接口开始，参数：email="+email+" ,signCode="+signCode+" ,userName="+userName+" ,companyName="+companyName);
    	try {
	    	if(email==null || userName==null || companyName==null || signCode==null ||"".equals(email) || "".equals(signCode)|| "".equals(userName)|| "".equals(companyName)){
	    		status="2";//参数不合法
	    	}else{
	    		SimpleDateFormat dfs = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	    		final BASE64Decoder decoder = new BASE64Decoder();
	    		String time=new String(decoder.decodeBuffer(signCode), "UTF-8");
	    		Date date = dfs.parse(dfs.format(new Date()));
	            Calendar now = Calendar.getInstance();
	            now.setTime(date);
	            int year = now.get(Calendar.YEAR);
	            int month = now.get(Calendar.MONTH) + 1; // 0-based!
	            int day = now.get(Calendar.DAY_OF_MONTH);
	            int hour =now.get(Calendar.HOUR_OF_DAY);
	        	if(time.equals(String.valueOf(year+month+day+hour))){
	        		user = userQueryService.getUserByEmail(email);
		    		if(user==null){
		    			user = new JSONObject();
			            user.put(User.USER_NAME, email.substring(0,email.indexOf("@")));
			            user.put(User.USER_EMAIL, email);
			            user.put(User.USER_PASSWORD, MD5.hash(email.substring(0,email.indexOf("@"))));
			            user.put("userRole", "defaultRole");
			            user.put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_VALID);
			            user.put("userGuideStep", 0);
			            user.put("userPoint", Symphonys.getInt("pointInit"));
			            user.put("userNickname", companyName+"-"+userName);
			            final Locale locale = Locales.getLocale();
			            user.put(UserExt.USER_LANGUAGE, locale.getLanguage() + "-" + locale.getCountry());
		
			            String userId = userMgmtService.symAddUser(user);
			            status="0";//开通成功
			            passWord=getStringRandom(8);
		    		}else{
		    			status="1";
		    		}
	        	}else{
	        		status="2";
	        	}
	    	}
    	} catch (Exception e) {
			e.printStackTrace();
			LOGGER.info("***************调用checkRpaUser接口异常，异常信息："+e);
			status="-1";//其他异常情况
		}
    	JSONObject returnJson = new JSONObject(); 
    	returnJson.put("status", status);
    	returnJson.put("passWord", passWord);
    	response.setCharacterEncoding("utf-8");
		response.setContentType("application/json; charset=utf-8");
		PrintWriter writer = response.getWriter();
		LOGGER.info("***************调用checkRpaUser接口结束，返回结果："+returnJson.toString());
		writer.write(returnJson.toString());
		writer.flush();
		writer.close();
    }
    
    
	
	//获取随机大小写字母加数字
	public String getStringRandom(int length){
		String val ="";
		Random random = new Random();
		//参数length，表示生成几位随机数
		for (int i = 0; i <length; i++) {
			String charOrNum=random.nextInt(2)%2==0?"char":"num";
			if("char".equalsIgnoreCase(charOrNum)){
				//输出是大写字母还是小写字母
				int temp=random.nextInt(2)%2==0?65:97;
				val += (char)(random.nextInt(26) + temp);
			}else if("num".equalsIgnoreCase(charOrNum)){
				val += String.valueOf(random.nextInt(10));
			}
		}
		return val;
	}

    
}
