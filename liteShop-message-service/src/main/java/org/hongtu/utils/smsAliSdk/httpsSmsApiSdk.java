//
//  Created by  fred on 2017/1/12.
//  Copyright © 2016年 Alibaba. All rights reserved.
//

package org.hongtu.utils.smsAliSdk;

import com.alibaba.cloudapi.sdk.client.ApacheHttpClient;
import com.alibaba.cloudapi.sdk.enums.HttpMethod;
import com.alibaba.cloudapi.sdk.enums.ParamPosition;
import com.alibaba.cloudapi.sdk.enums.Scheme;
import com.alibaba.cloudapi.sdk.model.ApiCallback;
import com.alibaba.cloudapi.sdk.model.ApiRequest;
import com.alibaba.cloudapi.sdk.model.ApiResponse;
import com.alibaba.cloudapi.sdk.model.HttpClientBuilderParams;

public class httpsSmsApiSdk extends ApacheHttpClient{
    public final static String HOST = "dfsns.market.alicloudapi.com";
    static httpsSmsApiSdk instance = new httpsSmsApiSdk();
    public static httpsSmsApiSdk getInstance(){return instance;}

    public void init(HttpClientBuilderParams httpClientBuilderParams){
        httpClientBuilderParams.setScheme(Scheme.HTTPS);
        httpClientBuilderParams.setHost(HOST);
        super.init(httpClientBuilderParams);
    }



    public void sendSms(String content , String template_id , String phone_number , ApiCallback callback) {
        String path = "/data/send_sms";
        ApiRequest request = new ApiRequest(HttpMethod.POST_FORM , path);
        request.addParam("content" , content , ParamPosition.BODY , true);
        request.addParam("template_id" , template_id , ParamPosition.BODY , true);
        request.addParam("phone_number" , phone_number , ParamPosition.BODY , true);

        sendAsyncRequest(request , callback);
    }

    public ApiResponse sendSmsSyncMode(String content , String template_id , String phone_number) {
        String path = "/data/send_sms";
        ApiRequest request = new ApiRequest(HttpMethod.POST_FORM , path);
        request.addParam("content" , content , ParamPosition.BODY , true);
        request.addParam("template_id" , template_id , ParamPosition.BODY , true);
        request.addParam("phone_number" , phone_number , ParamPosition.BODY , true);

        return sendSyncRequest(request);
    }

}