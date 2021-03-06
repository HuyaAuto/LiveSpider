package com.letcafe.controller.huya;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.letcafe.bean.HuYaProperties;
import com.letcafe.bean.HuYaTask;
import com.letcafe.service.CookieService;
import com.letcafe.service.HuYaTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;


@Controller
public class TaskPrizeGetter {

    private static final Logger logger = LoggerFactory.getLogger(TaskPrizeGetter.class);

    private CookieService cookieService;
    private HuYaTaskService huYaTaskService;
    private HuYaProperties huYaProperties;

    @Autowired
    public TaskPrizeGetter(CookieService cookieService, HuYaTaskService huYaTaskService, HuYaProperties huYaProperties) {
        this.cookieService = cookieService;
        this.huYaTaskService = huYaTaskService;
        this.huYaProperties = huYaProperties;
    }

    // 获取每天用户任务完成情况
    @Scheduled(cron = "${huya.task.status.time.setUserTaskStatus}")
    public void setUserTaskStatus() {
        logger.info("[Today Task Finish Status : MySQL] save successfully");
        for (HuYaTask huYaTask : currentHuYaTaskList()) {
            huYaTaskService.save(huYaTask);
        }
    }

    /**
     * 每天收取任务奖励
     */
    @Scheduled(cron = "${huya.task.prize.time.getAllTaskPrize}")
    public void getAllTaskPrize() {
        logger.info("[Task Prize : Auto Get] success");
        StringBuilder taskStringBuilder = new StringBuilder("Task = [");
        int successNumber = 0;
        for (HuYaTask huYaTask : currentHuYaTaskList()) {
            int code = getTaskPrizeByTaskId(huYaTask.getTaskId());
            if (code == 200) {
                taskStringBuilder.append(huYaTask.getTaskId()).append(",");
                successNumber ++;
            }
        }
        taskStringBuilder.deleteCharAt(taskStringBuilder.length() - 1);
        logger.info(taskStringBuilder.append("] has done prize getter, success number = ").append(successNumber).toString());
    }

    public Integer getTaskPrizeByTaskId(Integer taskId) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.add("cookie", cookieService.getUserCookieInRedis(huYaProperties.getYyId()));
        //body
        MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("m", "User");
        requestBody.add("do", "doGetTaskPrize");
        requestBody.add("callback", "jQuery172011150986730677803_" + System.currentTimeMillis());
        requestBody.add("taskId", taskId);
        requestBody.add("_", System.currentTimeMillis());
        //HttpEntity
        HttpEntity<MultiValueMap> requestEntity = new HttpEntity<>(requestBody, requestHeaders);
        //post
        ResponseEntity<String> responseEntity = restTemplate.postForEntity("https://q.huya.com/yy/index.php", requestEntity, String.class);

        String responseResult = responseEntity.getBody();
        if (responseResult != null) {
            responseResult = responseResult.substring(responseResult.indexOf("{"), responseResult.lastIndexOf("}") + 1);
            JSONObject jsonObject = JSONObject.parseObject(responseResult);
            return jsonObject.getInteger("status");
        }
        return -1;
    }

    public List<HuYaTask> currentHuYaTaskList() {
        List<HuYaTask> tasksList = new ArrayList<>();
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.add("cookie", cookieService.getUserCookieInRedis(huYaProperties.getYyId()));
        //body
        MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("m", "User");
        requestBody.add("do", "listTotal");
        requestBody.add("callback", "huyaNavUserCard");
        //HttpEntity
        HttpEntity<MultiValueMap> requestEntity = new HttpEntity<>(requestBody, requestHeaders);
        //post
        ResponseEntity<String> responseEntity = restTemplate.postForEntity("https://www.huya.com/member/task.php", requestEntity, String.class);

        int statusCode = responseEntity.getStatusCode().value();
        String entity = "";
        //如果状态响应码为200，则获取html实体内容或者json文件
        if (statusCode == 200) {
            //EntityUtils.toString (responseEntity,"utf-8")
            entity = responseEntity.getBody();
            entity = entity.substring(entity.indexOf("{"), entity.lastIndexOf("}") + 1);
            JSONObject rawJson = JSONObject.parseObject(entity);

            JSONObject huyaNavUserCard = rawJson.getJSONObject("data");
            JSONArray tasksArrayJson = huyaNavUserCard.getJSONArray("tasks");
            for (int i = 0; i < tasksArrayJson.size(); i++) {
                JSONObject taskJson = tasksArrayJson.getJSONObject(i);
                HuYaTask task = new HuYaTask(taskJson.getInteger("id"),
                        taskJson.getString("name"),
                        taskJson.getString("desc"),
                        taskJson.getString("enable"),
                        taskJson.getInteger("exper"),
                        taskJson.getString("icon"),
                        taskJson.getString("className"),
                        "" + taskJson.get("awardPrize"),
                        taskJson.getInteger("progress"),
                        taskJson.getInteger("progressMode"),
                        taskJson.getInteger("targetLevel"),
                        taskJson.getInteger("type"));
                tasksList.add(task);
            }
            Collections.sort(tasksList);
        } else {
            logger.warn("[Http Entity] = " + entity);
        }
        return tasksList;
    }
}
