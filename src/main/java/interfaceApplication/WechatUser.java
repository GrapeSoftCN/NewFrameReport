package interfaceApplication;

import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import common.java.Concurrency.distributedLocker;
import common.java.JGrapeSystem.rMsg;
import common.java.apps.appIns;
import common.java.apps.appsProxy;
import common.java.check.checkHelper;
import common.java.interfaceModel.GrapeDBDescriptionModel;
import common.java.interfaceModel.GrapePermissionsModel;
import common.java.interfaceModel.GrapeTreeDBModel;
import common.java.nlogger.nlogger;
import common.java.security.codec;
import common.java.string.StringHelper;
import common.java.time.timeHelper;

public class WechatUser {
	private GrapeTreeDBModel wechatUser;
	private GrapeDBDescriptionModel gDbSpecField;
	private GrapePermissionsModel grapePermissionsModel;
	private static final String lockerName = "WechatUser_Query_Locker";
	private String pkString;
	private static HashMap<Integer, ScheduledExecutorService> ticktockThread = null;
	static {
		ticktockThread = new HashMap<>();
	}
	public WechatUser() {
		wechatUser = new GrapeTreeDBModel();
		gDbSpecField = new GrapeDBDescriptionModel();
		gDbSpecField.importDescription(appsProxy.tableConfig("WechatUser"));
		wechatUser.descriptionModel(gDbSpecField);
		grapePermissionsModel =new GrapePermissionsModel();
		grapePermissionsModel.importDescription(appsProxy.tableConfig("WechatUser"));
		wechatUser.permissionsModel(grapePermissionsModel);
		wechatUser.checkMode();
		pkString = wechatUser.getPk();
	}

	/**
	 * 参数验证
	 * 
	 * @param info
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private String CheckParam(int sdkUserID, String info) {
		int count = 0;
		String headimgurl = "", openid = "", phone = "";
		JSONObject object = JSONObject.toJSON(info);
		if (object != null && object.size() > 0) {
            object.remove("type");
			if (!object.containsKey("time")) { // 操作时间
				object.put("time", timeHelper.nowMillis());
			}
			if (object.containsKey("phone")) {
				phone = object.getString("phone");
			}
			if (!StringHelper.InvaildString(phone) || checkHelper.checkMobileNumber(phone)) {
				return rMsg.netMSG(1, "未填写手机号或者手机号填写错误");
			}
			if (object.containsKey("openid")) {
				openid = object.getString("openid");
				while (headimgurl.equals("") && count < 5) {
					// 获取微信用户的头像信息
					JSONObject userinfo = new Report().getWechatUserInfos(sdkUserID, openid);
					if (userinfo == null || userinfo.size() <= 0) {
						return rMsg.netMSG(false, "无效openid");
					}
					if (userinfo.containsKey("headimgurl")) {
						headimgurl = userinfo.getString("headimgurl");
					}
					count++;
				}
				object.put("headimgurl", headimgurl);
				object.put("time", timeHelper.nowMillis());
			} else {
				return rMsg.netMSG(false, "未获取到openId");
			}
		}
		return object.toJSONString();
	}

	/**
	 * 实名认证，新增一条用户信息
	 * 
	 * @param SDKUserID
	 * @param info
	 * @return
	 */
	public String insertOpenId(int SDKUserID, String info) {
		Object tip = null;
		String result = rMsg.netMSG(100, "实名认证失败");
		info = CheckParam(SDKUserID, info);
		// info = codec.DecodeFastJSON(info);
		if (!StringHelper.InvaildString(info)) {
			return rMsg.netMSG(false, "无效参数");
		}
		if (info.contains("errorcode")) {
			return info;
		}
		String openid = JSONObject.toJSON(info).getString("openid");
		// 验证该用户是否已实名过
		JSONObject object = FindOpenId(openid);
		if (object == null || object.size() <= 0) {
			tip = wechatUser.data(info).autoComplete().insertOnce();
		} else {
			tip = "已实名认证";
		}
		return tip != null ? rMsg.netMSG(0, "实名认证成功") : result;
	}

	/**
	 * 更新用户头像信息
	 * 
	 * @param openid
	 * @param info
	 */
	@SuppressWarnings("unchecked")
	protected String UpdateInfo(String openid, String info) {
		Object code = 99;
		String result = rMsg.netMSG(100, "更新用户信息失败");
		try {
			JSONObject object = JSONObject.toJSON(info);
			String headimg = (String) object.get("headimgurl");
			headimg = codec.DecodeHtmlTag(headimg);
			object.put("headimgurl", codec.decodebase64(headimg));
			if (object != null && object.size() > 0) {
				code = wechatUser.eq("openid", openid).data(object).updateEx();
			}
		} catch (Exception e) {
			nlogger.logout(e);
			code = 99;
		}
		return code != null ? rMsg.netMSG(0, "修改成功") : result;
	}

	/**
	 * 根据openid查询用户信息
	 * 
	 * @param openid
	 * @return
	 */
	protected JSONObject FindOpenId(String openid) {
		JSONObject object = null;
		try {
			object = wechatUser.eq("openid", openid).find();
		} catch (Exception e) {
			nlogger.logout(e);
			object = null;
		}
		return (object != null && object.size() > 0) ? object : null;
	}

	/**
	 * 对用户进行封号
	 * 
	 * @param openid
	 *            用户openid
	 * @param info
	 *            封号多久时间
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected String kickUser(String openid, String info) {
		String result = rMsg.netMSG(100, "操作失败");
		JSONObject obj = new JSONObject();
		JSONObject object = JSONObject.toJSON(info);
		if (object != null && object.size() > 0) {
			if (!object.containsKey("kickTime")) {
				obj.put("kickTime", 24 * 60 * 60 * 1000);
			}
			if (!object.containsKey("isdelete")) {
				obj.put("isdelete", "1");
			}
			if (!object.containsKey("time")) {
				obj.put("time", timeHelper.nowMillis());
			}
			Boolean res = wechatUser.eq("openid", openid).data(obj).updateEx();
			result = res ? rMsg.netMSG(0, "操作成功") : result;
		}
		return result;
	}

	/**
	 * 启动模块任务
	 * 
	 * @return
	 */
	public String startService() {
		appIns apps = appsProxy.getCurrentAppInfo();
		if (apps != null && !ticktockThread.containsKey(apps.appid)) {
			ScheduledExecutorService serv = Executors.newSingleThreadScheduledExecutor();
            distributedLocker servLocker = distributedLocker.newLocker(lockerName);
			if (servLocker.lock()) {// 判断是否锁定成功

				serv.scheduleAtFixedRate(() -> {
					appsProxy.setCurrentAppInfo(apps);
					distributedLocker sLocker = new distributedLocker(lockerName);
					if (!sLocker.isExisting()) {// 锁不存在了，退出服务
						WechatUser user = new WechatUser();
						user.stopService();
					}
					appsProxy.proxyCall("/GrapeReport/WechatUser/unkickUser", apps);
				}, 0, 1, TimeUnit.HOURS);
				ticktockThread.put(apps.appid, serv);
			}
		}
		return rMsg.netState(true);
	}

	/**
	 * 停止模块服务
	 * 
	 * @return
	 */
	public String stopService() {
		appIns apps = appsProxy.getCurrentAppInfo();
		if (ticktockThread.containsKey(apps.appid)) {
			ScheduledExecutorService serv = ticktockThread.get(apps.appid);
			if (!serv.isTerminated()) {
				serv.shutdown();
			}
			ticktockThread.remove(apps.appid);
		}
		(new distributedLocker(lockerName)).releaseLocker();
		return rMsg.netState(true);
	}

	/**
	 * 定时解封用户
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public String unkickUser() {
		JSONArray rsArray = wechatUser.scan((array) -> {
			JSONArray _aArray = new JSONArray();
			JSONObject object;
			for (Object obj : array) {
				object = (JSONObject) obj;
				if (object.getLong("isdelete") == 1 && object.getLong("kickTime")!=-1) {
					if (object.getLong("time") + object.getLong("kickTime") >= timeHelper.nowMillis()) {
						_aArray.add(object);
					}
				}
			}
			return _aArray;
		}, 30);
		JSONObject rjson;
		for (Object object : rsArray) {
			rjson = (JSONObject) object;
			wechatUser.or().eq(pkString, rjson.get(pkString));
		}
		JSONObject dataJson = new JSONObject();
		dataJson.put("isdelete", 0);
		dataJson.put("kickTime", 0);
		wechatUser.data(dataJson).updateAll();
		return "";
	}
}
