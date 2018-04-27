package interfaceApplication;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import common.java.JGrapeSystem.rMsg;
import common.java.apps.appsProxy;
import common.java.authority.plvDef.UserMode;
import common.java.authority.plvDef.plvType;
import common.java.database.dbFilter;
import common.java.interfaceModel.GrapeDBDescriptionModel;
import common.java.interfaceModel.GrapePermissionsModel;
import common.java.interfaceModel.GrapeTreeDBModel;
import common.java.json.JSONHelper;
import common.java.session.session;
import common.java.string.StringHelper;

/**
 * 举报类型管理
 * 
 *
 */
public class Rtype {
    private GrapeTreeDBModel rType;
    private GrapeDBDescriptionModel gDbSpecField;
    private GrapePermissionsModel grapePermissionsModel;
    private session se;
    private JSONObject userInfo = null;
    private String currentWeb = null;
    private Integer userType = null;
    private String pkString;

    public Rtype() {
        rType = new GrapeTreeDBModel();
        gDbSpecField = new GrapeDBDescriptionModel();
        gDbSpecField.importDescription(appsProxy.tableConfig("Rtype"));
        rType.descriptionModel(gDbSpecField);
        grapePermissionsModel = new GrapePermissionsModel();
        grapePermissionsModel.importDescription(appsProxy.tableConfig("Rtype"));
        rType.permissionsModel(grapePermissionsModel);
        pkString = rType.getPk();
        
        rType.checkMode();//开启权限检查

        se = new session();
        userInfo = se.getDatas();
        if (userInfo != null && userInfo.size() != 0) {
            currentWeb = userInfo.getString("currentWeb"); // 当前站点id
            userType =userInfo.getInt("userType");//当前用户身份
            
        }
    }

    /**
     * 新增举报类型
     * 
     * @param typeInfo
     * @return
     */
    @SuppressWarnings("unchecked")
    public String AddType(String typeInfo) {
        Object info = null;
        JSONObject obj = null;
        String result = rMsg.netMSG(100, "新增举报类型失败");
        typeInfo = CheckParam(typeInfo);
        if (typeInfo.contains("errorcode")) {
            return typeInfo;
        }
        JSONObject object = JSONObject.toJSON(typeInfo);
        JSONObject rMode = new JSONObject(plvType.chkType, plvType.powerVal).puts(plvType.chkVal, 100);//设置默认查询权限
    	JSONObject uMode = new JSONObject(plvType.chkType, plvType.powerVal).puts(plvType.chkVal, 200);
    	JSONObject dMode = new JSONObject(plvType.chkType, plvType.powerVal).puts(plvType.chkVal, 300);
    	object.put("rMode", rMode.toJSONString()); //添加默认查看权限
    	object.put("uMode", uMode.toJSONString()); //添加默认修改权限
    	object.put("dMode", dMode.toJSONString()); //添加默认删除权限
        if (object != null && object.size() > 0) {
            object.put("wbid", currentWeb);
            info = rType.data(object).insertOnce();
            obj = (info != null) ? findById(info.toString()) : new JSONObject();
        }
        return (obj != null && obj.size() > 0) ? rMsg.netMSG(0, obj) : result;
    }

    /**
     * 修改举报类型
     * 
     * @param id
     * @param typeInfo
     * @return
     */
    public String UpdateType(String id, String typeInfo) {
        int code = 99;
        String result = rMsg.netMSG(100, "修改失败");
        typeInfo = CheckParam(typeInfo);
        if (typeInfo.contains("errorcode")) {
            return typeInfo;
        }
        JSONObject object = JSONObject.toJSON(typeInfo);
        if (object != null && object.size() > 0) {
            code = rType.eq(pkString, id).data(object).updateEx() ? 0 : 99;
        }
        return code == 0 ? rMsg.netMSG(0, "修改成功") : result;
    }

    /**
     * 删除
     * 
     * @param id
     * @return
     */
    public String DeleteType(String id) {
        return DeleteBatchType(id);
    }

    // 批量删除
    public String DeleteBatchType(String ids) {
        long code = 0;
        String[] value = null;
        String result = rMsg.netMSG(100, "删除失败");
        if (StringHelper.InvaildString(ids)) {
            value = ids.split(",");
        }
        if (value != null) {
            rType.or();
            for (String tid : value) {
                rType.eq(pkString, tid);
            }
            code = rType.deleteAll();
        }
        return code > 0 ? rMsg.netMSG(0, "删除成功") : result;
    }
//
//    /**
//     * 分页
//     * 
//     * @param ids
//     * @param pageSize
//     * @return
//     */
//    public String PageType(int ids, int pageSize) {
//        return search(ids, pageSize, null);
//    }

    /**
     * 分页
     * 
     * @param ids
     * @param pageSize
     * @return
     */
    public String PageType(int ids, int pageSize) {
    	long total = 0;
    	JSONArray array = rType.dirty().page(ids, pageSize);
        total = rType.count();
        return rMsg.netPAGE(ids, pageSize, total, array);
    }
    /**
     * 条件分页
     * 
     * @param ids
     * @param pageSize
     * @param info
     * @return
     */
    public String search(int ids, int pageSize, String info) {
        long total = 0;
        if (StringHelper.InvaildString(info)) {
            JSONArray condArray = buildCond(info);
            if (condArray != null && condArray.size() > 0) {
                rType.where(condArray);
            } else {
                return rMsg.netPAGE(ids, pageSize, total, new JSONArray());
            }
        }
        //判断当前用户身份：系统管理员，网站管理员
    	if (UserMode.root>userType && userType>= UserMode.admin) { //判断是否是网站管理员
    		rType.eq("wbid", currentWeb);
		}
        JSONArray array = rType.dirty().page(ids, pageSize);
        total = rType.count();
        return rMsg.netPAGE(ids, pageSize, total, (array != null && array.size() > 0) ? array : new JSONArray());
    }

    /**
     * 举报类型查询，封装成{typeId:typeName,...}
     * 
     * @param //tid
     * @return
     */
    @SuppressWarnings("unchecked")
    protected JSONObject FindByIds(String tids) {
        String[] value = null;
        JSONArray array = null;
        JSONObject rObject = new JSONObject(), object;
        String id, typeName;
        if (StringHelper.InvaildString(tids)) {
            value = tids.split(",");
        }
        if (value != null) {
            rType.or();
            for (String tid : value) {
                rType.eq(pkString, tid);
            }
            array = rType.and().eq("wbid", currentWeb).select();
        }
        if (array != null && array.size() > 0) {
            for (Object obj : array) {
                object = (JSONObject) obj;
                id = object.getMongoID(pkString);
                typeName = object.getString("TypeName");
                rObject.put(id, typeName);
            }
        }
        return rObject;
    }

    /**
     * 参数验证
     * 
     * @param typeInfo
     * @return
     */
    private String CheckParam(String typeInfo) {
        String typeName = "";
        if (StringHelper.InvaildString(typeInfo)) {
            return rMsg.netMSG(1, "参数不能为空");
        }
        JSONObject object = JSONObject.toJSON(typeInfo);
        if (object != null && object.size() > 0) {
            if (object.containsKey("TypeName")) {
                typeName = object.getString("TypeName");
                if (findByName(typeName)) {
                    return rMsg.netMSG(2, "该举报类型已存在");
                }
            }
        }
        return typeInfo;
    }

    /**
     * 验证新添加的举报类型是否存在
     * 
     * @param name
     * @return
     */
    private boolean findByName(String name) {
        JSONObject object = null;
        object = rType.eq("TypeName", "name").eq("wbid", currentWeb).find();
        return object != null && object.size() > 0;
    }

    /**
     * 类型查询显示
     * 
     * @param tid
     * @return
     */
    private JSONObject findById(String tid) {
        JSONObject object = null;
        if (StringHelper.InvaildString(tid)) {
            object = rType.eq(pkString, "tid").find();
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
