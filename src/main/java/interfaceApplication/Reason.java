package interfaceApplication;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import common.java.JGrapeSystem.rMsg;
import common.java.apps.appsProxy;
import common.java.authority.plvDef.UserMode;
import common.java.database.dbFilter;
import common.java.interfaceModel.GrapeDBDescriptionModel;
import common.java.interfaceModel.GrapePermissionsModel;
import common.java.interfaceModel.GrapeTreeDBModel;
import common.java.string.StringHelper;

/**
 * 举报拒绝/完结事由管理
 * 
 *
 */
public class Reason {
    private GrapeTreeDBModel reason;
    private GrapeDBDescriptionModel gDbSpecField;
    private GrapePermissionsModel grapePermissionsModel;
    private String currentWeb = null;
    private Integer userType = 0;
    private String pkString;

    public Reason() {

        reason = new GrapeTreeDBModel();
        
        gDbSpecField = new GrapeDBDescriptionModel();
        gDbSpecField.importDescription(appsProxy.tableConfig("Reason"));
        reason.descriptionModel(gDbSpecField);
        grapePermissionsModel = new GrapePermissionsModel();
        grapePermissionsModel.importDescription(appsProxy.tableConfig("Reason"));
        reason.permissionsModel(grapePermissionsModel);
        pkString =reason.getPk();
        reason.checkMode();//开启权限检查
    }

    /**
     * 新增举报拒绝/完结事由
     * 
     * @param typeInfo
     * @return
     */
    public String AddReson(String typeInfo) {


        @SuppressWarnings("unused")
		int code =0;
        JSONObject obj =  JSONObject.toJSON(typeInfo);

        code = reason.data(obj).insertOnce() instanceof JSONObject ?0:99;
        String result = rMsg.netMSG(100, "新增举报拒绝/完结事由失败");
        return (obj != null && obj.size() > 0) ? rMsg.netMSG(0, obj) : result;
    }

    /**
     * 修改举报拒绝/完结事由
     * 
     * @param id
     * @param typeInfo
     * @return
     */
    public String UpdateReson(String id, String typeInfo) {
        boolean objects =false;
        String result = rMsg.netMSG(100, "修改失败");

        if (typeInfo.contains("errorcode")) {
            return typeInfo;
        }
        JSONObject object = JSONObject.toJSON(typeInfo);
        if (object != null && object.size() > 0) {
        	objects = reason.eq(pkString, id).data(object).updateEx();
        }
        return result = objects  ? rMsg.netMSG(0, "修改成功") : result;
    }

    /**
     * 批量删除
     * @param ids
     * @return
     */
    public String DeleteReson(String ids) {
        long code = 0;
        String[] value = null;
        String result = rMsg.netMSG(100, "删除失败");
        if (!StringHelper.InvaildString(ids)) {
            value = ids.split(",");
        }
        if (value != null) {
            reason.or();
            for (String tid : value) {
                reason.eq(pkString, tid);
            }
            code = reason.deleteAll();
        }
        return code > 0 ? rMsg.netMSG(0, "删除成功") : result;
    }

    /**
     * 分页
     * 
     * @param ids
     * @param pageSize
     * @return
     */
    public String PageReson(int ids, int pageSize) {
        return PageByReson(ids, pageSize, null);
    }

    /**
     * 条件分页
     * 
     * @param ids
     * @param pageSize
     * @param info
     * @return
     */
    public String PageByReson(int ids, int pageSize, String info) {
        long total = 0;
        if (!StringHelper.InvaildString(info)) {
            JSONArray condArray = buildCond(info);
            if (condArray != null && condArray.size() > 0) {
                reason.where(condArray);
            } else {
                return rMsg.netPAGE(ids, pageSize, total, new JSONArray());
            }
        }
//        判断当前用户身份：系统管理员，网站管理员
    	if (UserMode.root>userType && userType>= UserMode.admin) { //判断是否是网站管理员
    		reason.eq("wbid", currentWeb);
		}
        JSONArray array = reason.dirty().page(ids, pageSize);
        total = reason.count();
        return rMsg.netPAGE(ids, pageSize, total, (array != null && array.size() > 0) ? array : new JSONArray());
    }

    /**
     * 事由使用次数+1
     * @param name
     * @return
     */
    @SuppressWarnings("unchecked")
    public String addUseTime(String name) {
        int code = 99;
        String result = rMsg.netMSG(100, "操作失败");
        if (StringHelper.InvaildString(name)) {
            return rMsg.netMSG(3, "该拒绝/完成事由不存在");
        }
        reason.eq("Rcontent", name);
        JSONObject object = reason.dirty().find();
        if (object != null && object.size() > 0) {
            object.put("count", Integer.parseInt(object.getString("count")) + 1);
            code = reason.data(object).updateEx()  ? 0 : 99;
        }
        result = code == 0 ? rMsg.netMSG(0, "新增次数成功") : result;
        return result;
    }

    /**
     * 参数验证
     *
     * @param typeInfo
     * @return
     */
    @SuppressWarnings("unused")
	private String CheckParam(String typeInfo) {
        String typeName = "";
        if (StringHelper.InvaildString(typeInfo)) {
            return rMsg.netMSG(1, "非法参数");
        }
        JSONObject object = JSONObject.toJSON(typeInfo);
        if (object != null && object.size() > 0) {
            if (object.containsKey("Rcontent")) {
                typeName = object.getString("Rcontent");
                if (findByName(typeName)) {
                    return rMsg.netMSG(2, "举报拒绝/完结事由已存在");
                }
            }
        }
        return typeInfo;
    }

    /**
     * 验证新添加的Reson是否存在
     * 
     * @param name
     * @return
     */
    private boolean findByName(String name) {
        JSONObject object = null;
        object = reason.eq("TypeName", name).eq("wbid", currentWeb).find();
        return object != null && object.size() > 0;
    }

    /**
     * 类型查询显示
     * 
     * @param tid
     * @return
     */
    @SuppressWarnings("unused")
	private JSONObject findById(String tid) {
        JSONObject object = null;
        if (!StringHelper.InvaildString(tid)) {
            object = reason.eq(pkString, tid).find();
        }
        return object;
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
}
