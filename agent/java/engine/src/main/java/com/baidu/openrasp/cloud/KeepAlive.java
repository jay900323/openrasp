/*
 * Copyright 2017-2018 Baidu Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.baidu.openrasp.cloud;

import com.baidu.openrasp.cloud.model.CloudCacheModel;
import com.baidu.openrasp.cloud.model.CloudRequestUrl;
import com.baidu.openrasp.cloud.model.GenericResponse;
import com.baidu.openrasp.config.Config;
import com.baidu.openrasp.cloud.Utils.CloudUtils;
import com.baidu.openrasp.messaging.LogConfig;
import com.baidu.openrasp.plugin.js.engine.JSContext;
import com.baidu.openrasp.plugin.js.engine.JSContextFactory;
import com.baidu.openrasp.plugin.js.engine.JsPluginManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.mozilla.javascript.Scriptable;

import java.util.HashMap;
import java.util.Map;

/**
 * @description: 创建rasp与云控的心跳线程并初始化rasp的config
 * @author: anyang
 * @create: 2018/09/17 16:55
 */
public class KeepAlive {
    private static final int KEEPALIVE_DELAY = 60000;

    public KeepAlive() {
        new Thread(new KeepAliveThread()).start();
    }

    class KeepAliveThread implements Runnable {
        @Override
        public void run() {
            while (true) {
                String content = new Gson().toJson(GenerateParameters());
                String url = CloudRequestUrl.CLOUD_HEART_BEAT_URL;
                GenericResponse response = new CloudHttpPool().request(url, content);
                if (response != null) {
                    handler(response);
                }else {
                    CloudManager.LOGGER.warn("[OpenRASP] Cloud Control Send HeartBeat Failed");
                }
                try {
                    Thread.sleep(KEEPALIVE_DELAY);
                } catch (InterruptedException e) {
                    //continue next loop
                }
            }
        }
    }

    public static Map<String, Object> GenerateParameters() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("rasp_id", CloudCacheModel.getInstance().getRaspId());
        params.put("plugin_version", CloudCacheModel.getInstance().getPluginVersion());
        params.put("config_time", CloudCacheModel.getInstance().getConfigTime());
        return params;
    }

    private static void handler(GenericResponse response) {
        if (response.getStatus() != null && response.getStatus() == 0) {
            Object configTime = CloudUtils.getValueFromData(response, "config_time");
            if (configTime != null) {
                CloudCacheModel.getInstance().setConfigTime(((JsonPrimitive) configTime).getAsLong());
            }
            Map<String,Object> pluginMap = CloudUtils.getMapFromData(response, "plugin");
            if (pluginMap != null) {
                if (pluginMap.get("plugin_version") != null) {
                    CloudCacheModel.getInstance().setPluginVersion(((JsonPrimitive) pluginMap.get("plugin_version")).getAsString());
                }
                if (pluginMap.get("plugin") != null) {
                    String plugin = ((JsonPrimitive) pluginMap.get("plugin")).getAsString();
                    if (!plugin.equals(CloudCacheModel.getInstance().getPlugin())) {
                        CloudCacheModel.getInstance().setPlugin(plugin);
                        JsPluginManager.updatePluginAsync();
                    }
                }
            }
            Map<String, Object> configMap = CloudUtils.getMapFromData(response, "config");
            if (configMap != null) {
                Object object = configMap.get("algorithm.config");
                if (object != null) {
                    JsonObject jsonObject = (JsonObject)object;
                    if (!jsonObject.toString().equals(CloudCacheModel.getInstance().getAlgorithmConfig())){
                        CloudCacheModel.getInstance().setAlgorithmConfig(jsonObject.toString());
                        if (Config.getConfig().getCloudSwitch()) {
                            String algorithmConfig = CloudCacheModel.getInstance().getAlgorithmConfig();
                            if (algorithmConfig != null) {
                                injectRhino(algorithmConfig);
                            }
                        }
                    }
                }
                Config.getConfig().loadConfigFromCloud(configMap, true);
                //云控下发配置时动态添加或者删除syslog
                LogConfig.syslogManager();
            }

        }
    }

    private static void injectRhino(String script) {
        script = "RASP.algorithmConfig =" + script;
        JSContext cx = JSContextFactory.enterAndInitContext();
        Scriptable globalScope = cx.getScope();
        cx.evaluateString(globalScope, script, "algorithmConfig", 1, null);
    }
}