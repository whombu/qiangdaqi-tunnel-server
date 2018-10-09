package cn.bigforce.weapptunnel.controller;

import cn.bigforce.weapptunnel.bean.HostConfig;
import cn.bigforce.weapptunnel.bean.ResponseObject;
import cn.bigforce.weapptunnel.bean.TunnelRequestObject;
import cn.bigforce.weapptunnel.conf.TunnelConfiguration;
import cn.bigforce.weapptunnel.tool.Hash;
import cn.bigforce.weapptunnel.ws.SpringWebSocketHandler;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Created by LAB520 on 2017/3/11.
 */
@Controller
@RequestMapping(value = "/")
public class WebSocketController {

    @RequestMapping(value = "get/wsurl")
    @ResponseBody
    public ResponseObject getWebSocketUrl(@RequestBody TunnelRequestObject request){
        if(request.getTcKey() == null)
            return new ResponseObject(4,"秘钥让狗吃了?");
        //校验签名
        if(!isSignatureValid(request.getSignature(), request.getData(), request.getTcKey()))
            return new ResponseObject(1,"验证失败");

        if(request.getDataEncode()==null||"json".equals(request.getDataEncode())){

            String host = TunnelConfiguration.host;
            String tunnelId = getTunnelId();

            JSONObject json = new JSONObject(request.getData());
            String receiveUrl = json.getString("receiveUrl");
            String protocolType = json.getString("protocolType");

            System.out.println("tckey" + request.getTcKey());
            SpringWebSocketHandler.addBusinessServer(request.getTcId(), new HostConfig(receiveUrl, request.getTcKey()));
            SpringWebSocketHandler.addTunnelId(tunnelId);

            String connectUrl = String.format("%s://%s/ws?tunnelId=%s&tcId=%s", protocolType, host, tunnelId, request.getTcId());
            JSONObject response = new JSONObject().put("connectUrl", connectUrl).put("tunnelId",tunnelId);

            String data = response.toString();
            String signature = Hash.sha1(data + request.getTcKey());
            return new ResponseObject(data, signature);
        }
        return new ResponseObject(2, "不支持的格式");

    }

    @RequestMapping(value = "ws/push")
    @ResponseBody
    public ResponseObject sendMessage(@RequestBody TunnelRequestObject request){
       HostConfig hostConfig = SpringWebSocketHandler.getBusinessServer(request.getTcId());
       if(hostConfig==null)
           return new ResponseObject(6, "你之前调用过get/wsurl吗?!");

       request.setTcKey(hostConfig.getTcKey());

        //校验签名
        if(!isSignatureValid(request.getSignature(), request.getData(), request.getTcKey()))
            return new ResponseObject(1,"验证失败");

        if(request.getDataEncode()==null||"json".equals(request.getDataEncode())){
            JSONObject json = new JSONArray(request.getData()).getJSONObject(0);

            String type = json.getString("type");
            List tunnelIds = json.getJSONArray("tunnelIds").toList();
            String content = json.getString("content");
            SpringWebSocketHandler handler = new SpringWebSocketHandler();

            //如果消息类型是close
            if("close".equals(type)){
                for (Object o: tunnelIds){
                    String tunnelId = (String) o;
                    handler.closeTunnelByTunnelId(tunnelId);
                }
                return new ResponseObject(0, "关闭信道无任何返回值");
            }//消息类型是message
            else if("message".equals(type)){
                JSONArray invalid = new JSONArray();

                for (Object o: tunnelIds){
                    String tunnelId = (String) o;
                    boolean b = handler.sendMessageByTunnelId(tunnelId, content);
                    if(!b)
                        invalid.put(tunnelId);
                }

                JSONObject response = new JSONObject();
                response.put("invalidTunnelIds", invalid);

                String data = response.toString();
                String signature = Hash.sha1(data + request.getTcKey());
                return new ResponseObject(data, signature);
            }
        }
        return new ResponseObject(2, "不支持的格式");
    }


    private boolean isSignatureValid(String signature, String data, String tcKey){
        String temp = Hash.sha1(data+tcKey);
        System.out.print(temp);
        return temp!=null && temp.equals(signature);
    }
    private String getTunnelId(){
        return UUID.randomUUID().toString();
    }
}
