package com.letcafe.controller.huya;

import com.letcafe.bean.HuYaLiveInfo;
import com.letcafe.bean.mongo.LiveInfoLog;
import com.letcafe.service.HuYaGameTypeService;
import com.letcafe.service.HuYaLiveInfoService;
import com.letcafe.service.LiveInfoLogService;
import com.letcafe.util.HttpUtils;
import com.letcafe.util.JacksonUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.letcafe.util.HuYaUtils.YY_ID;

@Controller
@RequestMapping("/getter/huya/liveInfo")
public class LiveInfoGetter {

    private final Logger logger = LoggerFactory.getLogger(LiveInfoGetter.class);

    private HuYaGameTypeService huYaGameTypeService;
    private HuYaLiveInfoService huYaLiveInfoService;
    private LiveInfoLogService liveInfoLogService;

    public LiveInfoGetter() {
    }

    @Autowired
    public LiveInfoGetter(HuYaGameTypeService huYaGameTypeService,
                          HuYaLiveInfoService huYaLiveInfoService,
                          LiveInfoLogService liveInfoLogService) {
        this.huYaGameTypeService = huYaGameTypeService;
        this.huYaLiveInfoService = huYaLiveInfoService;
        this.liveInfoLogService = liveInfoLogService;
    }

    public List<HuYaLiveInfo> listHuYaLiveByGid(int gid){
        RestTemplate restTemplate = new RestTemplate();
        List<HuYaLiveInfo> huYaLiveInfoList = new ArrayList<>(20);
        //body
        Map<String, Object> requestBody = new HashMap<>(3);
        requestBody.put("m", "Live");
        requestBody.put("do", "getProfileRecommendList");
        requestBody.put("gid", gid);
        ResponseEntity<String> responseEntity = restTemplate.getForEntity("https://www.huya.com/cache10min.php?m={m}&do={do}&gid={gid}", String.class, requestBody);
        if (responseEntity.getStatusCode() == HttpStatus.OK && responseEntity.getBody() != null) {
            JSONObject jsonObject = new JSONObject(responseEntity.getBody());
            JSONArray jsonArray = jsonObject.getJSONArray("data");
            for (int i = 0; i < jsonArray.length(); i ++) {
                HuYaLiveInfo huYaLiveInfo = JacksonUtils.readValue(jsonArray.get(i).toString(), HuYaLiveInfo.class);
                huYaLiveInfoList.add(huYaLiveInfo);
            }
            return huYaLiveInfoList;
        } else {
            return null;
        }
    }


    private void addHuYaLiveInfoLog(int gid, long logCurrentTime) throws Exception {
        HttpClient client = HttpClients.createDefault();
        String url="https://www.huya.com/cache10min.php?m=Live&do=getProfileRecommendList&gid=" + gid;
        HttpResponse response = HttpUtils.getRawHtml(client, url);
        int StatusCode = response.getStatusLine().getStatusCode();
        String entity;
        //如果状态响应码为200，则获取html实体内容或者json文件
        if(StatusCode == 200){
            entity = EntityUtils.toString (response.getEntity(),"utf-8");
            JSONObject jsonObject = new JSONObject(entity);
            JSONArray jsonArray = jsonObject.getJSONArray("data");
            for (int i = 0; i < jsonArray.length(); i ++) {
                HuYaLiveInfo huya = JacksonUtils.readValue(jsonArray.get(i).toString(), HuYaLiveInfo.class);
                LiveInfoLog liveInfoLog = new LiveInfoLog(
                        huya.getUid(),
                        huya.getSex(),
                        huya.getActivityCount(),
                        huya.getNick(),
                        "" + huya.getGid(),
                        huya.getAttendeeCount(),
                        huya.getLiveHost(),
                        huya.getGame(),
                        new Timestamp(logCurrentTime));
                liveInfoLogService.save(liveInfoLog);
            }
        }else {
            EntityUtils.consume(response.getEntity());
        }
    }

    // Every 30 minutes update all huya live information in MySQL
    @Scheduled(cron = "0 20/30 * * * *")
    public void updateAllHuYaLiveInfo() {
        long startTime = System.currentTimeMillis();
        List<Integer> gidList = huYaGameTypeService.listAllGid();
        for (Integer gid : gidList) {
            List<HuYaLiveInfo> liveList = listHuYaLiveByGid(gid);
            for (HuYaLiveInfo liveInfo : liveList) {
                huYaLiveInfoService.saveOrUpdate(liveInfo);
            }
        }
        long endTime = System.currentTimeMillis();
        logger.info("saveOrUpdate all Live.[cost time] = " + (endTime - startTime) / 1000 + "s");
    }

    // Every 30 minutes update all huya log information in MySQL
    @Scheduled(cron = "0 20/30 * * * *")
    public void insertAll() throws Exception {
        String currentTime = "" + System.currentTimeMillis();
        long logCurrentTime = Long.valueOf(currentTime.substring(0, currentTime.length() - 3) + "000");
        addHuYaLiveInfoLog(1, logCurrentTime);
        logger.info("[log Time] = " + new Timestamp(logCurrentTime).toLocalDateTime() + ";[total mongo count] = " + liveInfoLogService.countAll());
    }

}
