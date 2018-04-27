package interfaceApplication;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import common.java.JGrapeSystem.rMsg;
import common.java.apps.appIns;
import common.java.apps.appsProxy;
import common.java.authority.plvDef.UserMode;
import common.java.cache.CacheHelper;
import common.java.checkCode.checkCodeHelper;
import common.java.database.DBHelper;
import common.java.database.db;
import common.java.database.dbFilter;
import common.java.interfaceModel.GrapeDBDescriptionModel;
import common.java.interfaceModel.GrapePermissionsModel;
import common.java.interfaceModel.GrapeTreeDBModel;
import common.java.interrupt.interrupt;
import common.java.nlogger.nlogger;
import common.java.offices.excelHelper;
import common.java.security.codec;
import common.java.sms.ruoyaMASDB;
import common.java.string.StringHelper;
import common.java.thirdsdk.wechatHelper;
import common.java.time.timeHelper;

public class Report {
	private GrapeTreeDBModel report;
	private GrapeDBDescriptionModel gDbSpecField;
	private GrapePermissionsModel grapePermissionsModel;
	private CacheHelper cache;
	private String currentWeb = null;
	private Integer userType = 0;
	private String userid = null;
	private String appid = null;
	private static ExecutorService rs = Executors.newFixedThreadPool(300);
	private String pkString;
	public Report() {
		appid = appsProxy.appidString();
		cache = new CacheHelper();
		pkString=report.getPk();
		report = new GrapeTreeDBModel();
		
		gDbSpecField = new GrapeDBDescriptionModel();
		gDbSpecField.importDescription(appsProxy.tableConfig("Report"));
		report.descriptionModel(gDbSpecField);
		
		grapePermissionsModel = new GrapePermissionsModel();
		grapePermissionsModel.importDescription(appsProxy.tableConfig("Report"));
		report.permissionsModel(grapePermissionsModel);
		
		report.checkMode();
	}

	/**
	 * 获取微信用户信息
	 * 
	 * @param sdkUserID
	 * @param openid
	 * @return
	 */
	protected JSONObject getWechatUserInfos(int sdkUserID, String openid) {
		JSONObject WechatuserInfo = null;
		wechatHelper wechatHelper = getWeChatHelper(sdkUserID);
		if (wechatHelper != null) {
			WechatuserInfo = wechatHelper.getUserInfo(openid);
		}
		return WechatuserInfo;
	}
	/**
	 * 查询个人举报件
	 * 
	 * @param idx
	 * @param pageSize
	 * @return
	 */
	public String showByUser(int idx, int pageSize) {
		long total = 0;
		JSONArray array = null;
		try {
			if (StringHelper.InvaildString(userid)) {
				return rMsg.netMSG(1, "当前登录信息失效，无法查看信息");
			}
			report.eq("userid", userid);
			array = report.dirty().field("_id,content,time,handletime,completetime,refusetime,state,reason").dirty().desc("time").page(idx, pageSize);
			total = report.count();
			report.clear();
		} catch (Exception e) {
			nlogger.logout(e);
			array = null;
		}
		return rMsg.netPAGE(idx, pageSize, total, (array != null && array.size() > 0) ? dencode(array) : new JSONArray());
	}

	/**
	 * 查询个人举报件
	 * 
	 * @param //idx
	 * @param //pageSize
	 * @return
	 */
	public String searchByUser(String userid, int no) {
		JSONArray array = null;
		try {
			array = new JSONArray();
			array = report.desc("time").eq("userid", userid).ne("state", Integer.valueOf(0)).limit(no).select();
		} catch (Exception e) {
			array = null;
		}
		array = dencode(array);
		array = getImage(array);
		return rMsg.netMSG(true, array);
	}

	/**
	 * 网页端新增举报
	 * 
	 * @param info
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public String AddReportWeb(String info) {
		Object rs = null;
		String wbid = currentWeb;
		long currentTime = timeHelper.nowMillis();
		long time = currentTime;
		String result = rMsg.netMSG(100, "新增举报件失败");
		try {
			JSONObject object = JSONObject.toJSON(info);
			if (object == null || object.size() <= 0) {
				return rMsg.netMSG(1, "无效参数");
			}
			String content = object.getString("content");
			content = codec.DecodeHtmlTag(content);
			object.put("content", content);
			if (object.containsKey("wbid")) {
				wbid = object.getString("wbid");
			}
			object.put("circulation", wbid);
			if (object.containsKey("time")) {
				time = object.getLong(time);
				if (time == 0 || time > currentTime) {
					time = currentTime;
				}
			}
			object.put("time", time);
			rs = report.data(object).autoComplete().insertOnce();
			result = rs != null ? rMsg.netMSG(0, "提交举报信息成功") : result;
			// 发送数据到kafka
			appsProxy.proxyCall("/GrapeSendKafka/SendKafka/sendData2Kafka/" + rs + "/int:2/int:1/int:0");
		} catch (Exception e) {
			nlogger.logout(e);
			result = rMsg.netMSG(100, "提交举报信息失败");
		}
		return result;
	}

	/**
	 * 获取用户openid，实名认证
	 * 
	 * @param code
	 * @param url
	 * @return
	 * 
	 */
	@SuppressWarnings("unchecked")
	public String getUserId(String code, String url, int sdkUserID) {
		String sign = "", openId = "", headImage = "";
		JSONObject object = new JSONObject();
		if (StringHelper.InvaildString(code) || StringHelper.InvaildString(url)) {
			return rMsg.netMSG(false, "无效code");
		}
		wechatHelper wechatHelper = getWeChatHelper(sdkUserID);
		if (wechatHelper == null) {
			return rMsg.netMSG(false, "无效sdkUserId");
		}
		try {
			// 获取微信签名
			url = codec.DecodeHtmlTag(url);
			url = URLEncoder.encode(url, "utf-8");
			sign = wechatHelper.signature(url);
			JSONObject signObj = JSONObject.toJSON(sign);
			if (signObj == null || signObj.size() <=0) {
				return rMsg.netMSG(false, "无效url");
			}
			signObj.put("appid", getwechatAppid(sdkUserID, "appid"));
			// 获取openid
			openId = wechatHelper.getOpenID(code);
			if (!StringHelper.InvaildString(openId)) {
				return rMsg.netMSG(false, "无法获取用户微信openid");
			}
			object.put("openid", openId);
			object.put("sign", signObj.toString());
			object.put("headimgurl", headImage);
			JSONObject userInfos = new WechatUser().FindOpenId(openId);
			if (userInfos != null && userInfos.size() > 0) {
				object.put("msg", "已实名认证");
				headImage = userInfos.getString("headimgurl");
				object.put("headimgurl", headImage);
				return rMsg.netMSG(0, object);
			}
		} catch (Exception e) {
			nlogger.logout(e);
		}
		object.put("msg", "未实名认证");
		return rMsg.netMSG(1, object);
	}

	/**
	 * 新增举报件
	 * 
	 * @param //sdkUserId
	 * @param info
	 * @return
	 */
	public String AddReport(String info) {
		int mode = 1; // 默认匿名举报
		String userid = "";
		String result = rMsg.netMSG(100, "新增举报件失败");
		info = checkParam(info);
		if (info.contains("errorcode")) {
			return info;
		}
		JSONObject object = JSONObject.toJSON(info);
		if (object != null && object.size() > 0) {
			if (object.containsKey("mode")) {
				mode = Integer.parseInt(object.getString("mode"));
			}
			if (object.containsKey("userid")) {
				userid = object.getString("userid");
			}
			if (!StringHelper.InvaildString(userid)) {
				return rMsg.netMSG(false, "无效openid");
			}
			switch (mode) {
			case 0: // 实名
				result = NonAnonymous(userid, info);
				break;
			case 1: // 匿名
				result = insert(codec.encodeFastJSON(info));
				break;
			}
		}
		return result;
	}

	/**
	 * 实名举报
	 * 
	 * @param userid
	 *            用户openid
	 * @param //object
	 *            用户提交的举报件信息
	 * @return
	 */
	private String NonAnonymous(String userid, String info) {
		String result = rMsg.netMSG(100, "提交失败");
		// 判断当前用户是否已实名认证过，未实名认证，则进行实名认证，并将举报信息存入缓存
		JSONObject obj = new WechatUser().FindOpenId(userid);
		if (obj == null || obj.size() <= 0) {
			cache.setget(userid, codec.encodeFastJSON(info), 10 * 3600);
			return rMsg.netMSG(4, "您还未实名认证,请实名认证");
		}
		result = RealName(info, obj);
		return result;
	}

	/**
	 * 实名认证，发送短信到用户
	 * 
	 * @param //object
	 * @param userInfos
	 * @return
	 */
	private String RealName(String info, JSONObject userInfos) {
		String phone = "", result = rMsg.netMSG(100, "获取验证码失败");
		JSONObject temp;
		String ckcode = checkCodeHelper.generateVerifyCode(6);
		if (userInfos != null && userInfos.size() > 0) {
			phone = userInfos.getString("phone");
		}
		if (!StringHelper.InvaildString(phone)) {
			// 发送短信
			result = SendVerity(phone, "验证码为：" + ckcode);
			if (!StringHelper.InvaildString(result)) {
				temp = JSONObject.toJSON(result);
				if (temp.getLong("errorcode") == 0) { // 发送短信息成功
					appIns env = appsProxy.getCurrentAppInfo();
					rs.execute(() -> {
						String phoneNo = userInfos.getString("phone");
						appsProxy.setCurrentAppInfo(env);
						String nextStep = appid + "/GrapeReport/Report/insert/" + codec.encodeFastJSON(info);
						interrupt._break(ckcode, phoneNo, nextStep);
					});
				}
			}
		}
		return result;
	}

	/**
	 * 发送短信验证码，每人每天5次 存入缓存 key：phone；value:{"time":day,"count":0}
	 * 
	 * @param phone
	 * @param text
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private String SendVerity(String phone, String text) {
		JSONObject obj;
		String tip = null;
		String result = rMsg.netMSG(2, "短信发送失败");
		int day = 0, count = 0, currentDay = timeHelper.getNowDay();
		CacheHelper ch = new CacheHelper();
		if (ch.get(phone) != null) {
			obj = JSONObject.toJSON(ch.get(phone));
			day = obj.getInt("time");
			if (day == currentDay && count == 5) {
				return rMsg.netMSG(1, "您今日短信发送次数已达上线");
			}
		}
		// 直接存入缓存
		tip = ruoyaMASDB.sendSMS(phone, text);
		if (!StringHelper.InvaildString(tip)) {
			count++;
			ch.setget(phone, new JSONObject("count", count).put("time", currentDay), 86400);
			result = rMsg.netMSG(0, "短信发送成功");
		}
		return result;
	}

	/**
	 * 举报件处理完成
	 * 
	 * @param id
	 * @param reson
	 * @return
	 */
	public String CompleteReport(String id, String reson) {
		int code = 99;
		JSONObject object = new JSONObject();
		if (StringHelper.InvaildString(reson)) {
			object = JSONObject.toJSON(reson);
			if (object == null || object.size() <= 0) {
				return rMsg.netMSG(1, "无效参数");
			}
		}
		code = OperaReport(id, object, 2);
		// 发送数据到kafka
		appsProxy.proxyCall("/GrapeSendKafka/SendKafka/sendData2Kafka/" + id + "/int:2/int:3/int:3");
		return code == 0 ? rMsg.netMSG(0, "") : rMsg.netMSG(100, "");
	}

	/**
	 * 举报拒绝
	 * 
	 * @param id
	 * @param reson
	 * @return
	 */
	public String RefuseReport(String id, String reson) {
		int code = 99;
		JSONObject object = new JSONObject();
		if (StringHelper.InvaildString(reson)) {
			object = JSONObject.toJSON(reson);
			if (object == null || object.size() <= 0) {
				if (object == null || object.size() < 0) {
					return rMsg.netMSG(1, "无效参数");
				}
			}
		}
		code = OperaReport(id, object, 3);
		// 发送数据到kafka
		appsProxy.proxyCall("/GrapeSendKafka/SendKafka/sendData2Kafka/" + id + "/int:2/int:3/int:4");
		return code == 0 ? rMsg.netMSG(0, "") : rMsg.netMSG(100, "");
	}

	/**
	 * 举报件正在处理
	 * 
	 * @param id
	 * @param typeInfo
	 * @return
	 */
	public String HandleReport(String id, String typeInfo) {
		int code = 99;
		JSONObject object = new JSONObject();
		if (StringHelper.InvaildString(typeInfo)) {
			object = JSONObject.toJSON(typeInfo);
			if (object == null || object.size() < 0) {
				return rMsg.netMSG(1, "无效参数");
			}
		}
		code = OperaReport(id, object, 1);
		// 发送数据到kafka
		appsProxy.proxyCall("/GrapeSendKafka/SendKafka/sendData2Kafka/" + id + "/int:2/int:3/int:2");
		return code == 0 ? rMsg.netMSG(0, "") : rMsg.netMSG(100, "");
	}

	/**
	 * 正在处理，完成，拒绝操作
	 * 
	 * @param id
	 * @param object
	 * @param type
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private int OperaReport(String id, JSONObject object, int type) {
		int code = 99;
		if (object != null && object.size() > 0) {
			switch (type) {
			case 1: // 正在处理
				object.put("handletime", timeHelper.nowMillis());
				object.put("state", 1);
				break;

			case 2: // 处理完成
				object.put("completetime", timeHelper.nowMillis());
				object.put("state", 2);
				break;
			case 3: // 拒绝处理
				object.put("completetime", timeHelper.nowMillis());
				object.put("state", 3);
				object.put("isdelete", 1);
				break;
			}
			code = report.eq(pkString, id).data(object).updateEx() ? 0 : 99;

		}
		return code;
	}

	/* 前台分页显示 */
	public String PageFront(String wbid, int idx, int pageSize, String info) {
		long total = 0;
		if (!StringHelper.InvaildString(info)) {
			JSONArray condArray = buildCond(info);
			if (condArray != null && condArray.size() > 0) {
				report.where(condArray);
			} else {
				return rMsg.netPAGE(idx, pageSize, total, new JSONArray());
			}
		}
		report.eq("wbid", wbid);
		JSONArray array = report.dirty().desc("time").desc(pkString).page(idx, pageSize);
		total = report.count();
		array = getImage(dencode(array));
		return rMsg.netPAGE(idx, pageSize, total, (array != null && array.size() > 0) ? array : new JSONArray());
	}

	// 分页
	public String PageReport(int idx, int pageSize) {
		return PageBy(idx, pageSize, null);
	}

	public String PageByReport(int ids, int pageSize, String info) {
		return PageBy(ids, pageSize, info);
	}

	public String PageBy(int idx, int pageSize, String info) {
		long total = 0;
		if (!StringHelper.InvaildString(currentWeb)) {
			return rMsg.netMSG(1, "当前登录已失效，无法查看举报信息");
			// return rMsg.netPAGE(idx, pageSize, total, new JSONArray());
		}
		if (UserMode.root > userType && userType >= UserMode.admin) { // 判断是否是网站管理员
			String[] webtree = getAllWeb();
			if (webtree != null && !webtree.equals("")) {
				report.or();
				for (String string : webtree) {
					report.eq("wbid", string);
				}
			}
			// report.and();
			// report.eq("circulation", currentWeb);
		}
		if (!StringHelper.InvaildString(info)) {
			JSONArray condArray = buildCond(info);
			if (condArray != null && condArray.size() > 0) {
				report.where(condArray);
			} else {
				return rMsg.netPAGE(idx, pageSize, total, new JSONArray());
			}
		}
		JSONArray array = report.dirty().desc("time").page(idx, pageSize);
		total = report.count();
		dencode(array);
		array = getImage(array);
		return rMsg.netPAGE(idx, pageSize, total, (array != null && array.size() > 0) ? array : new JSONArray());
	}

	// 尚未被处理的事件总数
	public String Count() {
		long count = 0;
		// 判断当前用户身份：系统管理员，网站管理员
		userType = 1000;
		if (UserMode.root > userType && userType >= UserMode.admin) { // 判断是否是网站管理员
			// 网站管理员
			if (!StringHelper.InvaildString(currentWeb)) {
				String[] webtree = getAllWeb();
				if (webtree != null && !webtree.equals("")) {
					for (String string : webtree) {
						count += report.eq("wbid", string).eq("state", 0).count();
					}
				}
			}
		} else if (userType >= UserMode.root) {
			// 系统管理员统计所有的未处理的举报件信息
			count = report.eq("state", 0).count();
		}
		return rMsg.netMSG(0, String.valueOf(count));
	}

	/**
	 * 举报信息流转，只能流转到下级，且由一级流转至二级
	 * 
	 * @project GrapeReport
	 * @package interfaceApplication
	 * @file Report.java
	 * 
	 * @param rid
	 *            当前举报件id
	 * @param targetWeb
	 *            流转目标网站id
	 * @return
	 *
	 */
	@SuppressWarnings("unchecked")
	public String circulationReport(String rid, String targetWeb) {
		String currentId = "";
		int code = 99;
		String result = rMsg.netMSG(100, "当前站点不支持转发");
		JSONObject obj = new JSONObject();
		obj.put("circulation", targetWeb);
		if (!StringHelper.InvaildString(currentWeb)) {
			// 判断当前网站是否为一级网站
			if (IsFirstWeb(currentId)) {
				code = report.eq(pkString, rid).data(obj).updateEx() ? 0 : 99;
			}
			result = code == 0 ? rMsg.netMSG(0, "已经流转至其他用户") : result;
		} else {
			result = rMsg.netMSG(99, "当前用户信息已失效，请重新登录");
		}
		return result;
	}

	/**
	 * 判断是否为一级网站
	 * 
	 * @project GrapeReport
	 * @package interfaceApplication
	 * @file Report.java
	 * 
	 * @param currentId
	 * @return
	 *
	 */
	private boolean IsFirstWeb(String currentId) {
		JSONObject tempobj = null;
		String temp = (String) appsProxy.proxyCall("/GrapeWebInfo/WebInfo/isFirstWeb/" + currentWeb);
		if (temp != null && temp.length() > 0) {
			tempobj = JSONObject.toJSON(temp);
		}
		return (tempobj != null && tempobj.size() > 0);
	}

	/**
	 * 导出举报件信息
	 * 
	 * @param info
	 *            查询条件
	 * @param file
	 *            导出文件名称
	 * @return
	 */
	public Object Export(String info, String file) {
		String reportInfo = searchExportInfo(info);
		if (!StringHelper.InvaildString(reportInfo)) {
			try {
				return excelHelper.out(reportInfo);
			} catch (Exception e) {
				nlogger.logout(e);
			}
		}
		return rMsg.netMSG(false, "导出异常");
	}

	/**
	 * 查询举报件信息
	 * 
	 * @param info
	 * @return
	 *
	 */
	private String searchExportInfo(String info) {
		JSONArray condArray = null;
		JSONArray array = null;
		if (!StringHelper.InvaildString(info)) {
			condArray = JSONArray.toJSONArray(info);
			if (condArray != null && condArray.size() != 0) {
				report.where(condArray);
			} else {
				return null;
			}
		}
		array = report.field("userid,Wrongdoer,content,slevel,mode,state,time,handletime,refusetime,completetime,attr").select();
		return (array != null && array.size() != 0) ? array.toJSONString() : null;
	}

	/**
	 * 获取当前站点的所有下级站点，包含当前站点
	 * 
	 * @return
	 */
	private String[] getAllWeb() {
		String[] webtree = null;
		if (!StringHelper.InvaildString(currentWeb)) {
			String webTree = (String) appsProxy.proxyCall("/GrapeWebInfo/WebInfo/getWebTree/" + currentWeb);
			webtree = webTree.split(",");
		}
		return webtree;
	}

	/**
	 * 查询举报件详情
	 * 
	 * @param id
	 * @return
	 */
	public String SearchById(String id) {
		if (StringHelper.InvaildString(id)) {
			return rMsg.netMSG(false, "无效举报件id");
		}
		JSONObject object = Search(id);
		// // 发送数据到kafka
		// appsProxy.proxyCall("/GrapeSendKafka/SendKafka/sendData2Kafka/" + id
		// + "/int:2/int:2/int:0");
		return rMsg.netMSG(true, getImage(object));
	}

	/**
	 * 查询举报件信息
	 * 
	 * @param id
	 * @return
	 */
	private JSONObject Search(String id) {
		JSONObject object = null;
		if (!StringHelper.InvaildString(id)) {
			object = report.eq(pkString, id).find();
		}
		return (object != null && object.size() > 0) ? dencode(object) : null;
	}

	/**
	 * 查询举报件信息
	 * 
	 * @param //id
	 * @return
	 */
	public String SearchReport(int idx, int pageSize, String condString) {
		long total = 0;
		JSONArray array = null;
		JSONArray condArray = buildCond(condString);
		if (condArray != null && condArray.size() > 0) {
			report.where(condArray);
		} else {
			return rMsg.netMSG(1, "无效条件");
		}
		total = report.dirty().count();
		array = report.page(idx, pageSize);
		return rMsg.netPAGE(idx, pageSize, total, array);
	}

	/**
	 * 查询个人相关的举报件的总数
	 * 
	 * @param userid
	 * @return
	 */
	public String CountById(String userid) {
		long count = 0;
		if (StringHelper.InvaildString(userid)) {
			return rMsg.netMSG(1, "无效用户id");
		}
		count = report.eq("userid", userid).count();
		return rMsg.netMSG(true, count);
	}

	/**
	 * 统计待处理举报
	 * 
	 * @return
	 */
	public String CountReport() {
		String wbid = "";
		long count = 0;
		report.eq("state", 0);
		if (!StringHelper.InvaildString(currentWeb)) {
			count = report.eq("wbid", wbid).count();
		}
		return rMsg.netMSG(0, String.valueOf(count));
	}

	/**
	 * 新增操作
	 * 
	 * @param info
	 * @return
	 */
	public String insert(String info) {
		String tip = null;
		JSONObject object = null;
		String result = rMsg.netMSG(100, "提交举报失败");
		info = codec.DecodeFastJSON(info);
		if (StringHelper.InvaildString(info)) {
			return rMsg.netMSG(false, "无效参数");
		}
		tip = report.data(info).autoComplete().insertOnce().toString();
		object = Search(tip);
		return (object != null && object.size() > 0) ? rMsg.netMSG(0, object) : result;
	}

	/**
	 * 新增举报件，参数验证编码
	 * 
	 * @param info
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private String checkParam(String info) {
		long time = 0;
		String result = rMsg.netMSG(1, "参数异常");
		if (!StringHelper.InvaildString(info)) {
			info = codec.DecodeFastJSON(info);
			JSONObject object = JSONObject.toJSON(info);
			if (object != null && object.size() > 0) {
				if (object.containsKey("content")) {
					String content = object.get("content").toString();
					if (!StringHelper.InvaildString(content)) {
						if (content.length() > 500) {
							return rMsg.netMSG(2, "举报内容超过指定字数");
						}
						object.put("content", codec.encodebase64(content));
					}
				}
				if (object.containsKey("time")) {
					time = object.getLong("time");
				}
				if (time == 0) {
					time = timeHelper.nowMillis();
				}
				object.put("time", time);
				result = object.toJSONString();
			}
		}
		return result;
	}

	/**
	 * 对用户进行封号
	 * 
	 * @param openid
	 * @param info
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public String kick(String openid, String info) {
		int code = 99;
		String result = rMsg.netMSG(100, "操作失败");
		JSONObject obj = new JSONObject();
		JSONObject object = JSONObject.toJSON(info);
		if (!object.containsKey(pkString)) {
			return rMsg.netMSG(2, "无法获取待处理举报信息");
		}
		result = new WechatUser().kickUser(openid, info);
		if (JSONObject.toJSON(result).getLong("errorcode") == 0) {
			if (object.containsKey("reason")) {
				obj.put("reason", object.getString("reason"));
			}
			code = OperaReport(object.getString(pkString), obj, 3);
			result = (code == 0) ? rMsg.netMSG(0, "操作成功") : result;
		}
		return result;
	}

	/**
	 * 统计已提交举报件增量 按时间区间查询 结束时间为当前时间 开始时间为当前时间减去管理员设置的接收短信的间隔时间
	 * 
	 * @param timediff
	 *            间隔时间
	 * @return
	 */
	protected long getReportCount(long timediff) {
		long count = 0;
		long currentTime = timeHelper.nowMillis();
		long startTime = currentTime - timediff;
		count = report.gt("time", startTime).lt("time", currentTime).eq("state", 1).count();
		return count;
	}
	
	/**
	 * 微信帮助类
	 * 
	 * @param sdkUserID
	 * @return
	 */
	public wechatHelper getWeChatHelper(int sdkUserID) {
		wechatHelper wechatHelper = null;
		if (sdkUserID != 0) {
			String _appid = getwechatAppid(sdkUserID, "appid");
			String _appsecret = getwechatAppid(sdkUserID, "appsecret");
			if ((!StringHelper.InvaildString(_appid)) && (!StringHelper.InvaildString(_appsecret))) {
				wechatHelper = new wechatHelper(_appid, _appsecret);
			}
		}
		return wechatHelper;
	}
	
	/**
	 * 获取appid，appsecret
	 * 
	 * @param id
	 * @param key
	 * @return
	 */
	public String getwechatAppid(int id, String key) {
		DBHelper helper = new DBHelper("localdb", "sdkuser");
		db db = helper.bind(appid);
		JSONObject object = db.eq("id", id).field("configString").find();
		String value = "";
		if (object != null && object.size() > 0) {
			if (object.containsKey("configstring")) {
				object = JSONObject.toJSON(object.getString("configstring"));
				if (object != null && object.size() > 0) {
					if (object.containsKey(key)) {
						value = object.getString(key);
					}
				}
			}
		}
		return value;
	}
	
	 /**
     * 举报件内容解码
     * 
     * @param array
     * @return
     */
	@SuppressWarnings("unchecked")
    public JSONArray dencode(JSONArray array) {
        try {
            if (array == null || array.size() == 0) {
                return new JSONArray();
            }
            for (int i = 0; i < array.size(); i++) {
                JSONObject object = (JSONObject) array.get(i);
                array.set(i, dencode(object));
            }
        } catch (Exception e) {
            nlogger.logout(e);
            array = new JSONArray();
        }
        return array;
    }
    
    /**
     * 举报件内容解码
     * 
     * @param obj
     * @return
     */
    @SuppressWarnings("unchecked")
    public JSONObject dencode(JSONObject obj) {
        String temp;
        String[] fields = { "content", "reason" };
        if (obj != null && obj.size() > 0) {
            for (String field : fields) {
                if (obj.containsKey(field)) {
                    temp = obj.getString(field);
                    if (!StringHelper.InvaildString(temp)) {
                        temp = codec.DecodeHtmlTag(temp);
                        temp = codec.decodebase64(temp);
                        obj.put(field, temp);
                    }
                }
            }
        }
        return obj;
    }
    
    /**
     * 获取文件详细信息
     * 
     * @param array
     * @return
     */
    @SuppressWarnings("unchecked")
    public JSONArray getImage(JSONArray array) {
        String fid = "";
        JSONObject object;
        if (array == null || array.size() <= 0) {
            return new JSONArray();
        }
        fid = getFid(array); // 获取文件id
        JSONObject obj = getFileInfo(fid);
        if (obj != null && obj.size() > 0) {
            for (int i = 0; i < array.size(); i++) {
                object = (JSONObject) array.get(i);
                array.set(i, FillFileInfo(obj, object));
            }
        }
        return array;
    }
    
    /**
     * 获取文件id
     * 
     * @param array
     * @return
     */
    private String getFid(JSONArray array) {
        String fid = "", temp;
        JSONObject tempObj;
        if (array != null && array.size() > 0) {
            for (Object object : array) {
                tempObj = (JSONObject) object;
                if (tempObj != null && tempObj.size() > 0) {
                    temp = getFid(tempObj);
                    if (!StringHelper.InvaildString(temp)) {
                        fid += temp + ",";
                    }
                }
            }
        }
        return StringHelper.fixString(fid, ',');
    }
    
    /**
     * 获取文件信息
     * @param fid
     * @return
     */
    private JSONObject getFileInfo(String fid) {
        String temp = "";
        if (!StringHelper.InvaildString(fid)) {
            temp = appsProxy.proxyCall("/GrapeFile/Files/getFileByID/" + fid).toString();
        }
        return JSONObject.toJSON(temp);
    }
    
    /**
     * 获取文件id
     * 
     * @param object
     * @return
     */
    private String getFid(JSONObject object) {
        String fid = "";
        if (object != null && object.size() > 0) {
            if (object.containsKey("attr")) {
                fid = object.getString("attr");
            }
        }
        return fid;
    }
    
    /**
     * 整合参数，将JSONObject类型的参数封装成JSONArray类型
     * 
     * @param //object
     * @return
     */
    public JSONArray buildCond(String Info) {
        String key;
        Object value;
        JSONArray condArray = null;
        JSONObject object = JSONObject.toJSON(Info);
        dbFilter filter = new dbFilter();
        if (object != null && object.size() > 0) {
            for (Object object2 : object.keySet()) {
                key = object2.toString();
                value = object.get(key);
                filter.eq(key, value);
            }
            condArray = filter.build();
        } else {
            condArray = JSONArray.toJSONArray(Info);
        }
        return condArray;
    }

    public JSONObject getImage(JSONObject object) {
        // 获取fid
        String fid = getFid(object);
        JSONObject obj = getFileInfo(fid); // 获取文件信息
        if (obj != null && obj.size() > 0) {
            object = FillFileInfo(obj, object);
        }
        return object;
    }
    
    @SuppressWarnings("unchecked")
    private JSONObject FillFileInfo(JSONObject FileObj,JSONObject object) {
        String attrlist = "", filetype = "";
        String[] attr;
        JSONObject FileInfoObj;
        List<String> imgList = new ArrayList<String>();
        List<String> videoList = new ArrayList<String>();
        List<String> fileList = new ArrayList<String>();
        attr = object.getString("attr").split(",");
        int attrlength = attr.length;
        if (attrlength != 0 && !attr[0].equals("") || attrlength > 1) {
            for (int j = 0; j < attrlength; j++) {
                FileInfoObj = (JSONObject) FileObj.get(attr[j]);
                if (FileInfoObj != null && FileInfoObj.size() != 0) {
                    attrlist = FileInfoObj.get("filepath").toString();
                    if (FileInfoObj.containsKey("filetype")) {
                        filetype = FileInfoObj.getString("filetype");
                    }
                    object.put("attrFile" + j, FileInfoObj);
                    if ("1".equals(filetype)) {
                        imgList.add(attrlist);  //视频
                    }else if ("2".equals(filetype)) {
                        videoList.add(attrlist);
                    }else {
                        fileList.add(attrlist);
                    }
                }
            }
        }
        object.put("image", imgList.size() != 0 ? StringHelper.join(imgList) : "");
        object.put("video", videoList.size() != 0 ? StringHelper.join(videoList) : "");
        object.put("file", fileList.size() != 0 ? StringHelper.join(fileList) : "");
        return object;
    }
}
