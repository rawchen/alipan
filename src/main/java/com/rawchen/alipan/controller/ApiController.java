package com.rawchen.alipan.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rawchen.alipan.config.Constants;
import com.rawchen.alipan.entity.PanFile;
import com.rawchen.alipan.entity.TokenBody;
import com.rawchen.alipan.utils.FileUtil;
import com.rawchen.alipan.utils.HttpClientUtil;
import com.rawchen.alipan.utils.SignUtil;
import com.rawchen.alipan.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.*;

@CrossOrigin
@Controller
@Slf4j
public class ApiController {

	@Value("${alipan.parent_file_id}")
	String parentFileId;

	@Value("${alipan.api_url}")
	String apiUrl;

	@Value("${alipan.oauth_token_url}")
	String oauthTokenUrl;

	@Value("${alipan.open_api_url}")
	String openApiUrl;

	@Value("${alipan.password_file_name}")
	String passwordFileName;

	@Value("${alipan.app_id}")
	String appId;

	@Value("${alipan.app_secret}")
	String appSecret;

	private final String querryApi = "https://passport.aliyundrive.com/newlogin/qrcode/query.do?appName=aliyun_drive&fromSite=52&_bx-v=2.0.31";

	/**
	 * 文件对象
	 *
	 * @param fileId
	 * @return
	 */
	@Deprecated
	@ResponseBody
	@PostMapping(value = "/getFile/{fileId}")
	public PanFile getFile(@PathVariable("fileId") String fileId) {

		List<String> sign = SignUtil.sign(appId, Constants.DEVICE_ID, Constants.USER_ID, "0");
		createSession(sign.get(0), sign.get(2));
		JSONObject requestJson = new JSONObject();
		requestJson.put("drive_id", Constants.DEFAULT_DRIVE_ID);
		requestJson.put("file_id", fileId);
		Map<String, String> headerMap = new HashMap<>();
		headerMap.put("Content-Type", "application/json");
		headerMap.put("Authorization", "Bearer " + Constants.ACCESS_TOKEN);
		headerMap.put("x-canary", "client=web,app=adrive,version=v3.17.0");
		headerMap.put("x-device-id", Constants.DEVICE_ID);
		headerMap.put("x-signature", sign.get(2));

		String result = HttpClientUtil.doPost(apiUrl + "/file/get",
				requestJson.toString(), headerMap);
		JSONObject jsonObject = JSONObject.parseObject(result);
		PanFile file = new PanFile();
		file.setFileId((String) jsonObject.get("file_id"));
		file.setType((String) jsonObject.get("type"));
		file.setName((String) jsonObject.get("name"));
		file.setCreatedAt((String) jsonObject.get("created_at"));
		file.setFileExtension((String) jsonObject.get("file_extension"));
		file.setParentFileId((String) jsonObject.get("parent_file_id"));
		file.setSize(((Number) jsonObject.get("size")).longValue());
		String url =  jsonObject.getString("url");
		url = (StrUtil.isEmpty(url)) ? "https://" : url;
		file.setUrl(url);
		return file;
	}

	/**
	 * 文件对象(Open)
	 *
	 * @param fileId
	 * @return
	 */
	@ResponseBody
	@PostMapping(value = "/getFileOpen/{fileId}")
	public PanFile getFileOpen(@PathVariable("fileId") String fileId) {

		JSONObject requestJson = new JSONObject();
		requestJson.put("drive_id", Constants.DEFAULT_DRIVE_ID);
		requestJson.put("file_id", fileId);
		requestJson.put("fields", "url,name_path");
		requestJson.put("video_thumbnail_width", 300);
		requestJson.put("image_thumbnail_width", 1920);
		Map<String, String> headerMap = new HashMap<>();
		headerMap.put("Content-Type", "application/json");
		headerMap.put("Authorization", "Bearer " + Constants.ACCESS_TOKEN_OPEN);

		String result = HttpClientUtil.doPost(openApiUrl + "/adrive/v1.0/openFile/get", requestJson.toString(), headerMap);
		JSONObject jsonObject = JSONObject.parseObject(result);
		PanFile file = new PanFile();
		file.setFileId((String) jsonObject.get("file_id"));
		file.setType((String) jsonObject.get("type"));
		file.setName((String) jsonObject.get("name"));
		file.setCreatedAt((String) jsonObject.get("created_at"));
		file.setFileExtension((String) jsonObject.get("file_extension"));
		file.setParentFileId((String) jsonObject.get("parent_file_id"));
		file.setSize(((Number) jsonObject.get("size")).longValue());
		String url = jsonObject.getString("url");
		url = (StrUtil.isEmpty(url)) ? "https://" : url;
		file.setUrl(url);
		return file;
	}

	/**
	 * 文件列表
	 *
	 * @param fileId
	 * @return
	 */
	@Deprecated
	@ResponseBody
	@PostMapping(value = "/getFolderV1/{fileId}")
	public List<PanFile> getFolderV1(@PathVariable("fileId") String fileId, @RequestParam(required = false) String password) {

		List<String> sign = SignUtil.sign(appId, Constants.DEVICE_ID, Constants.USER_ID, "0");
		createSession(sign.get(0), sign.get(2));

		JSONObject requestJson = new JSONObject();
		requestJson.put("all", false);
		requestJson.put("drive_id", Constants.DEFAULT_DRIVE_ID);
		requestJson.put("fields", "*");
		requestJson.put("image_thumbnail_process", "image/resize,w_256/format,avif");
		requestJson.put("image_url_process", "image/resize,w_1920/format,avif");
		requestJson.put("limit", 100);
		requestJson.put("marker", "");
		requestJson.put("url_expire_sec", 14400);
		requestJson.put("order_by", "name");
		requestJson.put("order_direction", "ASC");
		requestJson.put("parent_file_id", fileId);
		requestJson.put("video_thumbnail_process", "video/snapshot,t_120000,f_jpg,m_lfit,w_256,ar_auto,m_fast");

		Map<String, String> headerMap = new HashMap<>();
		headerMap.put("Content-Type", "application/json");
		headerMap.put("Authorization", "Bearer " + Constants.ACCESS_TOKEN);
		headerMap.put("x-canary", "client=web,app=adrive,version=v3.17.0");
		headerMap.put("x-device-id", Constants.DEVICE_ID);
		headerMap.put("x-signature", sign.get(2));

		// https://api.aliyundrive.com/adrive/v3
		String result = HttpClientUtil.doPost("" + "/file/list", requestJson.toString(), headerMap);
		JSONObject jsonObject = JSONObject.parseObject(result);
		//如果请求到json体不是空且不可用就刷新token
		if (jsonObject.getJSONArray("items") == null) {
			log.error("获取文件夹列表错误: {}", result);
		}

		if (jsonObject.get("code") != null
				&& (("AccessTokenInvalid".equals(jsonObject.get("code")))
				|| ("AccessTokenExpired".equals(jsonObject.get("code")))
				|| ("ForbiddenDriveNotValid".equals(jsonObject.get("code"))))) {
			log.info(refreshOriginal());
			headerMap.put("Authorization", "Bearer " + Constants.ACCESS_TOKEN_OPEN);
			requestJson.put("drive_id", Constants.DEFAULT_DRIVE_ID);
			List<String> signRetry = SignUtil.sign(appId, Constants.DEVICE_ID, Constants.USER_ID, "0");
			createSession(signRetry.get(0), signRetry.get(2));
			// https://api.aliyundrive.com/adrive/v3
			result = HttpClientUtil.doPost("" + "/file/list", requestJson.toString(), headerMap);
			jsonObject = JSONObject.parseObject(result);
		}

		ArrayList<PanFile> panFiles = new ArrayList<>();
		// 成功返回了文件列表
		if (jsonObject.getJSONArray("items") != null) {
			JSONArray items = jsonObject.getJSONArray("items");
			for (int i = 0; i < items.size(); i++) {
				PanFile file = new PanFile();
				file.setFileId((String) items.getJSONObject(i).get("file_id"));
				file.setType((String) items.getJSONObject(i).get("type"));
				file.setName((String) items.getJSONObject(i).get("name"));
				file.setParentFileId((String) items.getJSONObject(i).get("parent_file_id"));
				file.setCreatedAt((String) items.getJSONObject(i).get("created_at"));
				file.setEncrypted(false);
				if ("file".equals(items.getJSONObject(i).get("type"))) {
					file.setPreviewUrl((String) items.getJSONObject(i).get("thumbnail"));
					file.setFileExtension((String) items.getJSONObject(i).get("file_extension"));
					file.setSize(((Number) items.getJSONObject(i).get("size")).longValue());
					String url = items.getJSONObject(i).getString("url");
					url = (url == null) ? "https://" : url;
					file.setUrl(url);
				}
				panFiles.add(file);
			}
		}

		//文件列表中密码文件的位置
		int passwordIndex = -1;

		for (int i = 0; i < panFiles.size(); i++) {
			//列表中如果有
			if (passwordFileName.equals(panFiles.get(i).getName())) {
				//找到一个名字为password的文件，如果传参为空就说明没传密码，直接返回一个文件且encrypted为true
				if (password == null || "".equals(password)) {
					panFiles.clear();
					panFiles.add(new PanFile(passwordFileName, true, "file"));
					break;
				} else {
					//找到一个名字为password的文件，但是传了密码参数
					String folderPasswd = StringUtil.clearStr(HttpClientUtil.doGet(panFiles.get(i).getUrl(), null, new HashMap<>(), null));
					//如果密码没对上
					if (!password.equals(folderPasswd)) {
						panFiles.clear();
						panFiles.add(new PanFile(passwordFileName, true, "file"));
						break;
					} else {
						//密码对上了，在文件列表中删除这个密码文件(同一文件夹只能一个)
						 passwordIndex = i;
					}
				}
			}
		}
		if (passwordIndex != -1) {
			panFiles.remove(passwordIndex);
		}
		return panFiles;
	}

	/**
	 * 文件列表(Open)
	 *
	 * @param fileId
	 * @return
	 */
	@ResponseBody
	@Cacheable(value = "folderCache",
			key = "#fileId + '_' + #request.getHeader('X-Session-Id')",
			unless = "#result == null or #result.isEmpty() or (#result.size() == 1 and #result.get(0).encrypted)")
	@PostMapping(value = "/getFolderOpen/{fileId}")
	public List<PanFile> getFolder(@PathVariable("fileId") String fileId, @RequestParam(required = false) String password, HttpServletRequest request) {

		String sessionId = request.getHeader("X-Session-Id");
		if (sessionId == null) {
			return Collections.emptyList();
		}
		JSONObject requestJson = new JSONObject();
		requestJson.put("all", false);
		requestJson.put("drive_id", Constants.DEFAULT_DRIVE_ID);
		requestJson.put("fields", "*");
		requestJson.put("limit", 100);
		requestJson.put("marker", "");
		requestJson.put("url_expire_sec", 14400);
		requestJson.put("order_by", "name");
		requestJson.put("order_direction", "ASC");
		requestJson.put("parent_file_id", fileId);
		requestJson.put("video_thumbnail_width", 300);
		requestJson.put("image_thumbnail_width", 300);

		Map<String, String> headerMap = new HashMap<>();
		headerMap.put("Content-Type", "application/json");
		headerMap.put("Authorization", "Bearer " + Constants.ACCESS_TOKEN_OPEN);

		String result = HttpClientUtil.doPost(openApiUrl + "/adrive/v1.0/openFile/list", requestJson.toString(), headerMap);
		JSONObject jsonObject = JSONObject.parseObject(result);
		//如果请求到json体不是空且不可用就刷新token
		if (jsonObject.getJSONArray("items") == null) {
			log.error("获取文件夹列表错误: {}", result);
		}

		if (jsonObject.get("code") != null
				&& (("AccessTokenInvalid".equals(jsonObject.get("code")))
				|| ("AccessTokenExpired".equals(jsonObject.get("code")))
				|| ("UserNotAllowedAccessDrive".equals(jsonObject.get("code")))
				|| ("ForbiddenDriveNotValid".equals(jsonObject.get("code"))))) {
			log.info(refreshOpen());
			headerMap.put("Authorization", "Bearer " + Constants.ACCESS_TOKEN_OPEN);
			result = HttpClientUtil.doPost(openApiUrl + "/adrive/v1.0/openFile/list", requestJson.toString(), headerMap);
			jsonObject = JSONObject.parseObject(result);
		}

		ArrayList<PanFile> panFiles = new ArrayList<>();
		// 成功返回了文件列表
		if (jsonObject.getJSONArray("items") != null) {
			JSONArray items = jsonObject.getJSONArray("items");
			for (int i = 0; i < items.size(); i++) {
				PanFile file = new PanFile();
				file.setFileId((String) items.getJSONObject(i).get("file_id"));
				file.setType((String) items.getJSONObject(i).get("type"));
				file.setName((String) items.getJSONObject(i).get("name"));
				file.setParentFileId((String) items.getJSONObject(i).get("parent_file_id"));
				file.setCreatedAt((String) items.getJSONObject(i).get("created_at"));
				file.setEncrypted(false);
				if ("file".equals(items.getJSONObject(i).get("type"))) {
					file.setPreviewUrl((String) items.getJSONObject(i).get("thumbnail"));
					file.setFileExtension((String) items.getJSONObject(i).get("file_extension"));
					file.setSize(((Number) items.getJSONObject(i).get("size")).longValue());
					String url = items.getJSONObject(i).getString("url");
					url = (url == null) ? "https://" : url;
					file.setUrl(url);
					// 超过100M的图片不显示预览图（开放平台不提供）
					if (StrUtil.isNotEmpty(file.getFileExtension())
							&& (file.getFileExtension().equalsIgnoreCase("jpg") || file.getFileExtension().equalsIgnoreCase("jpeg") || file.getFileExtension().equalsIgnoreCase("png"))
							&& file.getSize() >= 104857600) {
						file.setPreviewUrl(null);
					}
				}
				panFiles.add(file);
			}
		}

		//文件列表中密码文件的位置
		int passwordIndex = -1;

		for (int i = 0; i < panFiles.size(); i++) {
			//列表中如果有
			if (passwordFileName.equals(panFiles.get(i).getName())) {
				//找到一个名字为password的文件，如果传参为空就说明没传密码，直接返回一个文件且encrypted为true
				if (password == null || "".equals(password)) {
					panFiles.clear();
					panFiles.add(new PanFile(passwordFileName, true, "file"));
					break;
				} else {
					//找到一个名字为password的文件，但是传了密码参数
					String folderPasswd = StringUtil.clearStr(HttpClientUtil.doGet(panFiles.get(i).getUrl(), null, new HashMap<>(), null));
					//如果密码没对上
					if (!password.equals(folderPasswd)) {
						panFiles.clear();
						panFiles.add(new PanFile(passwordFileName, true, "file"));
						break;
					} else {
						//密码对上了，在文件列表中删除这个密码文件(同一文件夹只能一个)
						passwordIndex = i;
					}
				}
			}
		}
		if (passwordIndex != -1) {
			panFiles.remove(passwordIndex);
		}
		return panFiles;
	}

	/**
	 * 获取下载链接，过期4小时(Open)
	 *
	 * @param fileId
	 * @return
	 */
	@ResponseBody
	@PostMapping(value = "/getDownloadUrl/{fileId}")
	public PanFile getDownloadUrl(@PathVariable("fileId") String fileId) {

		JSONObject requestJson = new JSONObject();
		requestJson.put("drive_id", Constants.DEFAULT_DRIVE_ID);
		requestJson.put("file_id", fileId);
		requestJson.put("expire_sec", 14400);
		Map<String, String> headerMap = new HashMap<>();
		headerMap.put("Content-Type", "application/json");
		headerMap.put("Authorization", "Bearer " + Constants.ACCESS_TOKEN_OPEN);

		String result = HttpClientUtil.doPost(openApiUrl + "/adrive/v1.0/openFile/getDownloadUrl",
				requestJson.toString(), headerMap);
		JSONObject jsonObject = JSONObject.parseObject(result);

		PanFile file = new PanFile();
		file.setFileId(fileId);
		file.setSize(((Number) jsonObject.get("size")).longValue());
		file.setUrl((String) jsonObject.get("url"));
		return file;
	}

	/**
	 * 获取下载链接，过期4小时
	 *
	 * @param fileId
	 * @return
	 */
//	@GetMapping(value = "/dOpen/{fileId}")
	public String download(@PathVariable("fileId") String fileId) {

		List<String> sign = SignUtil.sign(appId, Constants.DEVICE_ID, Constants.USER_ID, "0");
		createSession(sign.get(0), sign.get(2));

		JSONObject requestJson = new JSONObject();
		requestJson.put("drive_id", Constants.DEFAULT_DRIVE_ID);
		requestJson.put("file_id", fileId);
		requestJson.put("expire_sec", 14400);
		Map<String, String> headerMap = new HashMap<>();
		headerMap.put("Content-Type", "application/json");
		headerMap.put("Authorization", "Bearer " + Constants.ACCESS_TOKEN);

		headerMap.put("x-canary", "client=web,app=adrive,version=v3.17.0");
		headerMap.put("x-device-id", Constants.DEVICE_ID);
		headerMap.put("x-signature", sign.get(2));

		String result = HttpClientUtil.doPost(apiUrl + "/file/get_download_url",
				requestJson.toString(), headerMap);
		JSONObject jsonObject = JSONObject.parseObject(result);
		return "redirect:" + jsonObject.get("url");
	}

	/**
	 * 下载文件(Open)
	 *
	 * @param fileId
	 * @return
	 */
	@GetMapping(value = "/d/{fileId}")
	public String downloadOpen(@PathVariable("fileId") String fileId) {

		JSONObject requestJson = new JSONObject();
		requestJson.put("drive_id", Constants.DEFAULT_DRIVE_ID);
		requestJson.put("file_id", fileId);
		requestJson.put("expire_sec", 14400);
		Map<String, String> headerMap = new HashMap<>();
		headerMap.put("Content-Type", "application/json");
		headerMap.put("Authorization", "Bearer " + Constants.ACCESS_TOKEN_OPEN);

		String result = HttpClientUtil.doPost(openApiUrl + "/adrive/v1.0/openFile/getDownloadUrl",
				requestJson.toString(), headerMap);
//		log.info("下载结果：" + result);
		JSONObject jsonObject = JSONObject.parseObject(result);

		if (jsonObject != null) {
			String code = jsonObject.getString("code");
			if ("TooManyRequests".equals(code)) {
				return "redirect:" + "/toomany";
			} else {
				if (jsonObject.getString("url") != null) {
					return "redirect:" + jsonObject.getString("url");
				}
			}
		}
		return "redirect:" + "/toomany";
	}

	private void createSession(String publicKey, String signature) {
		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + Constants.ACCESS_TOKEN);
		headers.put("Content-Type", "application/json");
		headers.put("Referer", "https://www.aliyundrive.com");
		headers.put("x-canary", "client=web,app=adrive,version=v3.17.0");
		headers.put("x-device-id", Constants.DEVICE_ID);
		headers.put("x-signature", signature);

		JSONObject param01 = new JSONObject();
		param01.put("deviceName", "Redmi");
		param01.put("modelName", "M2012K11AC");
		param01.put("nonce", "0");
		param01.put("pubKey", publicKey);

		String createSessionResult = HttpRequest.post("https://api.aliyundrive.com/users/v1/users/device/create_session")
				.addHeaders(headers)
				.body(param01.toJSONString())
				.execute()
				.body();
		log.info("create_session: " + createSessionResult);
	}

	/**
	 * 获取Office文件在线预览url和token
	 * @see <a href="https://help.aliyun.com/document_detail/396287.html">阿里云智能媒体管理</a>
	 *
	 * @param fileId
	 * @return
	 */
	@ResponseBody
	@PostMapping(value = "/getOfficePreviewUrl/{fileId}")
	public Map<String, String> getOfficePreviewUrl(@PathVariable("fileId") String fileId) {

		JSONObject requestJson = new JSONObject();
		requestJson.put("drive_id", Constants.DEFAULT_DRIVE_ID);
		requestJson.put("file_id", fileId);
		Map<String, String> headerMap = new HashMap<>();
		headerMap.put("Content-Type", "application/json");
		headerMap.put("Authorization", "Bearer " + Constants.ACCESS_TOKEN);

		String result = HttpClientUtil.doPost(apiUrl + "/file/get_office_preview_url",
				requestJson.toString(), headerMap);
		JSONObject jsonObject = JSONObject.parseObject(result);
		String previewUrl = "";
		String accessToken = "";
		if (jsonObject != null) {
			previewUrl = (String) jsonObject.get("preview_url");
			accessToken = (String) jsonObject.get("access_token");
		}
		Map<String, String> map = new HashMap();
		map.put("preview_url", previewUrl);
		map.put("access_token", accessToken);
		return map;
	}

	/**
	 * 刷新token
	 *
	 * @return
	 */
	@ResponseBody
	@GetMapping(value = "/refresh")
	public String refresh() {
		log.info(refreshOriginal());
		log.info(refreshOpen());
		return "后台查看刷新结果";
	}

	/**
	 * 刷新token
	 *
	 * @return
	 */
	@ResponseBody
	@GetMapping(value = "/refresh_original")
	public String refreshOriginal() {
		log.info("执行刷新refresh_token_original");
		JSONObject requestJson = new JSONObject();
		requestJson.put("grant_type", "refresh_token");
		requestJson.put("refresh_token", Constants.getRefreshToken());
		String result = HttpClientUtil.doPost(apiUrl + "/account/token", requestJson.toString());
		if (StringUtil.isEmpty(result)) {
			return "refreshOriginal()出错：，检查接口：" + apiUrl + "/account/token";
		}
		JSONObject jsonObject = JSONObject.parseObject(result);
		if (jsonObject.get("code") != null) {
			return "refreshOriginal()出错：" + result;
		}
		if (jsonObject.get("code") == null && jsonObject.get("access_token") != null) {
			Constants.setAccessToken((String) jsonObject.get("access_token"));
			return "刷新配置文件成功，刷新 access_token 成功！";
		}
		return "其它问题，联系软件作者。";
	}

	/**
	 * 刷新token(open)
	 *
	 * @return
	 */
	@ResponseBody
	@GetMapping(value = "/refresh_open")
	public String refreshOpen() {
		log.info("执行刷新refresh_token_open");
		TokenBody tokenBodyOpen = new TokenBody();
		tokenBodyOpen.setGrantType("refresh_token");
		tokenBodyOpen.setRefreshToken(Constants.getRefreshTokenOpen());
		String result = HttpClientUtil.doPost(oauthTokenUrl, JSON.toJSONString(tokenBodyOpen));
		if (StringUtil.isEmpty(result)) {
			return "refreshOpen()出错：，检查接口：" + oauthTokenUrl;
		}
		JSONObject jsonObject = JSONObject.parseObject(result);
		if (jsonObject.get("code") != null) {
			return "refreshOpen()出错：" + result;
		}
		if (!StringUtil.isEmpty(jsonObject.getString("access_token"))) {
			Constants.setAccessTokenOpen(jsonObject.getString("access_token"));
			return "刷新配置文件成功，刷新 access_token(Open) 成功！";
		}
		return "其它问题，联系软件作者。";
	}

	/**
	 * 授权 code 获取 access_token
	 *
	 * @return
	 */
	@ResponseBody
	@RequestMapping(value = "/callback")
	public JSONObject callback(String code) {
		log.info("回调授权/callback，授权码: {}", code);
		JSONObject paramJson = new JSONObject();
		paramJson.put("client_id", appId);
		paramJson.put("client_secret", appSecret);
		paramJson.put("grant_type", "authorization_code");
		paramJson.put("code", code);
		String result = HttpClientUtil.doPost(openApiUrl + "/oauth/access_token", paramJson.toString());
		JSONObject jsonObject = JSONObject.parseObject(result);
		return jsonObject;
	}

	/**
	 * 刷新Token
	 *
	 * @return
	 */
	@ResponseBody
	@PostMapping(value = "/api/token")
	public JSONObject token(@RequestBody TokenBody tokenBody) {
		log.info("更新令牌/token，刷新token: {}", StringUtil.subStringShort(tokenBody.getRefreshToken()));
		JSONObject paramJson = new JSONObject();
		paramJson.put("client_id", appId);
		paramJson.put("client_secret", appSecret);
		paramJson.put("grant_type", tokenBody.getGrantType());
		paramJson.put("refresh_token", tokenBody.getRefreshToken());
		String result = HttpClientUtil.doPost(openApiUrl + "/oauth/access_token", paramJson.toString());
		JSONObject jsonObject = JSONObject.parseObject(result);
		return jsonObject;
	}

	@ResponseBody
	@PostMapping("/api/original_token/query")
	public JSONObject query(String t, String ck) {
		Map<String, Object> param = new HashMap<>();
		param.put("t", t);
		param.put("ck", ck);
		String post = HttpUtil.post(querryApi, param);
		return JSONObject.parseObject(post);
	}
}