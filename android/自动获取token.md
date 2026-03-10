SDK内封装了获取和刷新Token的过程，使用户无需手动处理复杂的认证逻辑和Token有效期管理，简化了开发流程，提升了开发效率，更加安全有效。本文介绍如何通过SDK方式获取Token。

## **背景信息**

| **通过SDK获取Token方式**     | **说明**                                                         |
| ---------------------------- | ---------------------------------------------------------------- |
| 通过智能语音交互SDK获取Token | 适用于通过智能语音交互SDK直接获取Token的场景， 建议您集成此SDK。 |
| 通过阿里云公共SDK获取Token   | 适用于当前智能语音交互暂未提供对应语言SDK获取Token的场景。       |

## 前提条件

-   已获取AccessKey ID和AccessKey Secret，具体操作，请参见[从这里开始](https://help.aliyun.com/zh/isi/getting-started/start-here)**。**
    
-   调用接口前，需配置环境变量，通过环境变量读取访问凭证。智能语音交互的AccessKey ID和AccessKey Secret的环境变量名：**ALIYUN\_AK\_ID**和**ALIYUN\_AK\_SECRET**。
    

## 通过智能语音交互SDK获取Token

### **调用示例（Java）**

从Maven服务器下载最新版本SDK，[下载demo源码ZIP包](https://gw.alipayobjects.com/os/bmw-prod/5d824f8b-cf20-4bb9-a22a-74463bff2b36.zip)。

```
<dependency>
    <groupId>com.alibaba.nls</groupId>
    <artifactId>nls-sdk-common</artifactId>
    <version>2.1.6</version>
</dependency>
```

Java代码获取访问令牌Token实现示例如下：

```
AccessToken accessToken = new AccessToken(System.getenv().get("ALIYUN_AK_ID"), System.getenv().get("ALIYUN_AK_SECRET"));
accessToken.apply();
String token = accessToken.getToken();
long expireTime = accessToken.getExpireTime();
```

-   `token`为服务端分配的Token，在Token过期失效前，可以一直使用，也支持在不同机器、进程或应用上同时使用该Token。
    
-   `expireTime`为此令牌的有效期时间戳，单位：秒。例如，1527592757换算为北京时间为2018/5/29 19:19:17，即Token在该时间之前有效，过期需要重新获取。
    

**说明**

当前获取Token需要获取AccessKey ID和AccessKey Secret，为了安全起见，一般不建议在端侧操作，比如在移动端等环境下保存AccessKey ID和AccessKey Secret，建议您在安全可靠的环境中使用AccessKey ID和AccessKey Secret。

如果您的使用场景是移动端APP，可以考虑自行在服务端搭建一个Token生成器的服务，将AccessKey ID和AccessKey Secret放在服务端，APP调用语音识别前，先向您的服务端请求下发Token，之后通过此Token向智能语音服务发起调用。

### **调用示例（C++）**

**说明**

-   Linux下安装工具要求如下：
    
    -   Glibc 2.5及以上
        
    -   Gcc4或Gcc5
        
-   Windows下：VS2013或VS2015，Windows平台需要您自己搭建工程。
    

1.  [下载 C++ Token SDK](https://gw.alipayobjects.com/os/bmw-prod/933cf60b-bdbb-4c62-a67d-f314c47e6952.zip)。
    
2.  编译示例。
    
    假设示例文件已解压至`path/to`路径下，在Linux终端依次执行如下命令编译运行程序。
    
    -   当您的开发环境支持通过CMake编译：
        
        1.  确认本地系统已安装Cmake2.4及以上版本。
            
        2.  切换目录：`cd path/to/sdk/lib`。
            
        3.  解压缩文件：`tar -zxvpf linux.tar.gz`。
            
        4.  切换目录：`cd path/to/sdk`。
            
        5.  执行编译脚本：`./build.sh`。
            
        6.  切换目录：`cd path/to/sdk/demo`。
            
        7.  执行获取Token示例：`./tokenDemo <yourAccessKeySecret> <yourAccessKeyId>`。
            
    -   当您的开发环境不支持通过CMake编译：
        
        1.  切换目录：`cd path/to/sdk/lib`。
            
        2.  解压缩文件：`tar -zxvpf linux.tar.gz`。
            
        3.  切换目录：`cd path/to/sdk/demo`。
            
        4.  使用g++编译命令编译示例程序：`g++ -o tokenDemo tokenDemo.cpp -I path/to/sdk/include/ -L path/to/sdk/lib/linux -ljsoncpp -lssl -lcrypto -lcurl -luuid -lnlsCommonSdk -D_GLIBCXX_USE_CXX11_ABI=0`。
            
        5.  指定库路径：`export LD_LIBRARY_PATH=path/to/sdk/lib/linux/`。
            
        6.  执行获取Token示例：`./tokenDemo <yourAccessKeySecret> <yourAccessKeyId>`。
            
3.  调用服务。
    
    C++获取访问令牌Token的示例代码如下：
    
    ```
    #include <iostream>
    #include "Token.h"
    
    using std::cout;
    using std::endl;
    using namespace AlibabaNlsCommon;
    
    //获取访问令牌TokenId
    int getTokenId(const char* keySecret, const char* keyId) {
        NlsToken nlsTokenRequest;
    
        /*设置阿里云账号KeySecret*/
        nlsTokenRequest.setKeySecret(getenv("ALIYUN_AK_SECRET"));
        /*设置阿里云账号KeyId*/
        nlsTokenRequest.setAccessKeyId(getenv("ALIYUN_AK_ID"));
    
        /*获取Token. 成功返回0, 失败返回-1*/
        if (-1 == nlsTokenRequest.applyNlsToken()) {
            cout << "Failed: " << nlsTokenRequest.getErrorMsg() << endl; /*获取失败原因*/
    
            return -1;
        } else {
            cout << "TokenId: " << nlsTokenRequest.getToken() << endl; /*获取TokenId*/
            cout << "TokenId expireTime: " << nlsTokenRequest.getExpireTime() << endl; /*获取Token有效期时间戳（秒）*/
    
            return 0;
        }
    }
    ```
    

## 通过阿里云公共SDK获取Token

使用阿里云公共SDK获取Token，建议采用RPC风格的API调用。发起一次RPC风格的CommonAPI请求，需要提供以下参数：

| **参数名**  | **参数值**                        | **说明**                     |
| ----------- | --------------------------------- | ---------------------------- |
| domain      | nls-meta.cn-shanghai.aliyuncs.com | 产品的通用访问域名，固定值。 |
| region\\_id | cn-shanghai                       | 服务的地域ID，固定值。       |
| action      | CreateToken                       | API的名称，固定值。          |
| version     | 2019-02-28                        | API的版本号，固定值。        |

通过阿里云SDK获取Token时，如果成功调用，则会返回如下报文：

![通过阿里云公共SDK获取Token](https://help-static-aliyun-doc.aliyuncs.com/assets/img/zh-CN/8506283661/p494131.png)

-   `Id`为本次分配的访问令牌Token，在Token过期失效前，可以一直使用，也支持在不同机器、进程或应用上同时使用该Token。
    
-   `ExpireTime`为此令牌的有效期时间戳，单位：秒。如，1527592757换算为北京时间为2018/5/29 19:19:17，即Token在该时间之前有效，过期需要重新获取。
    

### **调用示例（Java）**

1.  添加Java依赖。
    
    添加阿里云Java SDK的核心库（版本为3.7.1）和fastjson库。
    
    ```
    <dependency>
        <groupId>com.aliyun</groupId>
        <artifactId>aliyun-java-sdk-core</artifactId>
        <version>3.7.1</version>
    </dependency>
    
    <!-- http://mvnrepository.com/artifact/com.alibaba/fastjson -->
    <dependency>
        <groupId>com.alibaba</groupId>
        <artifactId>fastjson</artifactId>
        <version>1.2.83</version>
    </dependency>
    ```
    
2.  调用服务。
    
    获取访问令牌的示例代码如下：
    
    ```
    import com.alibaba.fastjson.JSON;
    import com.alibaba.fastjson.JSONObject;
    import com.aliyuncs.CommonRequest;
    import com.aliyuncs.CommonResponse;
    import com.aliyuncs.DefaultAcsClient;
    import com.aliyuncs.IAcsClient;
    import com.aliyuncs.exceptions.ClientException;
    import com.aliyuncs.http.MethodType;
    import com.aliyuncs.http.ProtocolType;
    import com.aliyuncs.profile.DefaultProfile;
    
    import java.text.SimpleDateFormat;
    import java.util.Date;
    
    public class CreateTokenDemo {
    
        // 地域ID
        private static final String REGIONID = "cn-shanghai";
        // 获取Token服务域名
        private static final String DOMAIN = "nls-meta.cn-shanghai.aliyuncs.com";
        // API版本
        private static final String API_VERSION = "2019-02-28";
        // API名称
        private static final String REQUEST_ACTION = "CreateToken";
    
        // 响应参数
        private static final String KEY_TOKEN = "Token";
        private static final String KEY_ID = "Id";
        private static final String KEY_EXPIRETIME = "ExpireTime";
    
    
        public static void main(String args[]) throws ClientException {
    
            String accessKeyId = System.getenv().get("ALIYUN_AK_ID");
            String accessKeySecret = System.getenv().get("ALIYUN_AK_SECRET");
    
            // 创建DefaultAcsClient实例并初始化
            DefaultProfile profile = DefaultProfile.getProfile(
                    REGIONID,
                    accessKeyId,
                    accessKeySecret);
    
            IAcsClient client = new DefaultAcsClient(profile);
            CommonRequest request = new CommonRequest();
            request.setDomain(DOMAIN);
            request.setVersion(API_VERSION);
            request.setAction(REQUEST_ACTION);
            request.setMethod(MethodType.POST);
            request.setProtocol(ProtocolType.HTTPS);
    
            CommonResponse response = client.getCommonResponse(request);
            System.out.println(response.getData());
            if (response.getHttpStatus() == 200) {
                JSONObject result = JSON.parseObject(response.getData());
                String token = result.getJSONObject(KEY_TOKEN).getString(KEY_ID);
                long expireTime = result.getJSONObject(KEY_TOKEN).getLongValue(KEY_EXPIRETIME);
                System.out.println("获取到的Token： " + token + "，有效期时间戳(单位：秒): " + expireTime);
                // 将10位数的时间戳转换为北京时间
                String expireDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(expireTime * 1000));
                System.out.println("Token有效期的北京时间：" + expireDate);
            }
            else {
                System.out.println("获取Token失败！");
            }
        }
    }
    ```
    

### **调用示例（Python）**

使用pip安装SDK。

1.  执行以下命令，通过pip安装Python SDK，版本为2.15.1。
    
    ```
    pip install aliyun-python-sdk-core==2.15.1 # 安装阿里云SDK核心库
    ```
    
2.  调用服务。
    
    示例代码如下：
    
    ```
    #! /usr/bin/env python
    # coding=utf-8
    import os
    import time
    import json
    from aliyunsdkcore.client import AcsClient
    from aliyunsdkcore.request import CommonRequest
    
    # 创建AcsClient实例
    client = AcsClient(
       os.getenv('ALIYUN_AK_ID'),
       os.getenv('ALIYUN_AK_SECRET'),
       "cn-shanghai"
    )
    
    # 创建request，并设置参数。
    request = CommonRequest()
    request.set_method('POST')
    request.set_domain('nls-meta.cn-shanghai.aliyuncs.com')
    request.set_version('2019-02-28')
    request.set_action_name('CreateToken')
    
    try: 
       response = client.do_action_with_exception(request)
       print(response)
    
       jss = json.loads(response)
       if 'Token' in jss and 'Id' in jss['Token']:
          token = jss['Token']['Id']
          expireTime = jss['Token']['ExpireTime']
          print("token = " + token)
          print("expireTime = " + str(expireTime))
    except Exception as e:
       print(e)
    ```
    

### **调用示例（GO）**

开发前请先安装[alibaba-cloud-sdk-go](https://github.com/aliyun/alibaba-cloud-sdk-go)，具体操作请参见[安装操作](https://github.com/aliyun/alibaba-cloud-sdk-go/blob/master/README-CN.md)。

示例代码如下：

```
package main
import (
    "fmt"
    "encoding/json"
    "github.com/aliyun/alibaba-cloud-sdk-go/sdk"
    "github.com/aliyun/alibaba-cloud-sdk-go/sdk/requests"
)

type TokenResult struct {
    ErrMsg string
    Token struct {
        UserId      string
        Id          string
        ExpireTime  int64
    }
  }

func main() {
    config := sdk.NewConfig()
    credential := credentials.NewAccessKeyCredential(os.Getenv("ALIYUN_AK_ID"), os.Getenv("ALIYUN_AK_SECRET"))
    client, err := sdk.NewClientWithOptions("cn-shanghai", config, credential)
    if err != nil {
        panic(err)
    }
    request := requests.NewCommonRequest()
    request.Method = "POST"
    request.Domain = "nls-meta.cn-shanghai.aliyuncs.com"
    request.ApiName = "CreateToken"
    request.Version = "2019-02-28"
    response, err := client.ProcessCommonRequest(request)
    if err != nil {
        panic(err)
    }
    fmt.Print(response.GetHttpStatus())
    fmt.Print(response.GetHttpContentString())

    var tr TokenResult
    err = json.Unmarshal([]byte(response.GetHttpContentString()), &tr)
    if err == nil {
        fmt.Println(tr.Token.Id)
        fmt.Println(tr.Token.ExpireTime)
    } else {
        fmt.Println(err)
    }
}
```

### **调用示例（PHP）**

安装前请确保环境使用的是PHP 7.2及以上版本，PHP SDK安装方式请参见[安装Alibaba Cloud SDK for PHP](https://github.com/aliyun/openapi-sdk-php/blob/master/docs/zh-CN/1-Installation.md) 。

1.  创建一个全局客户端。
    
2.  创建API请求，设置参数。
    
3.  发起请求，处理应答或异常。
    

PHP代码获取访问令牌Token代码示例如下：

```
<?php
require __DIR__ . '/vendor/autoload.php';
use AlibabaCloud\Client\AlibabaCloud;
use AlibabaCloud\Client\Exception\ClientException;
use AlibabaCloud\Client\Exception\ServerException;
/**
 * 第一步：设置一个全局客户端
 * 使用阿里云RAM账号的AccessKey ID和AccessKey Secret进行鉴权。
 */
AlibabaCloud::accessKeyClient(
            getenv('ALIYUN_AK_ID'),
            getenv('ALIYUN_AK_SECRET'))
            ->regionId("cn-shanghai")
            ->asDefaultClient();
try {
    $response = AlibabaCloud::nlsCloudMeta()
                            ->v20180518()
                            ->createToken()
                            ->request();
    print $response . "\n";
    $token = $response["Token"];
    if ($token != NULL) {
        print "Token 获取成功：\n";
        print_r($token);
        print "获取到的token是：" . $token["Id"] ."\n";
        date_default_timezone_set('Asia/Shanghai');
        print "获取到的token过期时间是：" . $token["ExpireTime"] . "\n";
    }
    else {
        print "token 获取失败\n";
    }
} catch (ClientException $exception) {
    // 获取错误消息
    print_r($exception->getErrorMessage());
} catch (ServerException $exception) {
    // 获取错误消息
    print_r($exception->getErrorMessage());
}
```

**说明**

上述示例代码是基于阿里云新版PHP SDK进行示例，请参见[Alibaba Cloud SDK for PHP](https://github.com/aliyun/openapi-sdk-php)。如果您已接入阿里云旧版PHP SDK，参见[aliyun-openapi-php-sdk](https://github.com/aliyun/aliyun-openapi-php-sdk.git)，仍然可以继续使用或者更新到新版SDK。

### **调用示例（Node.js）**

1.  安装Node.js SDK。
    
    建议使用npm完成Node.js依赖模块的安装，所有阿里官方的Node.js SDK都位于@alicloud下。
    
    假设Node.js SDK下载后的路径为`/path/to/aliyun-openapi-Node.js-sdk`。当基于SDK核心库进行开发时，请执行以下命令安装@alicloud/pop-core模块。
    
    命令中的`--save`会将模块写入应用的package.json文件中，作为依赖模块。
    
    ```
     npm install @alicloud/pop-core --save
    ```
    
    **说明**
    
    您也可以从GitHub上下载SDK，请参见[GitHub下载SDK](https://github.com/aliyun/aliyun-openapi-nodejs-sdk)。
    
2.  调用服务。
    
    示例代码如下：
    
    ```
    var RPCClient = require('@alicloud/pop-core').RPCClient;
    
    var client = new RPCClient({
      accessKeyId: process.env.ALIYUN_AK_ID,
      accessKeySecret: process.env.ALIYUN_AK_SECRET,
      endpoint: 'http://nls-meta.cn-shanghai.aliyuncs.com',
      apiVersion: '2019-02-28'
    });
    
    // => returns Promise
    // => request(Action, params, options)
    client.request('CreateToken').then((result) => {
        console.log(result.Token)
        console.log("token = " + result.Token.Id)
        console.log("expireTime = " + result.Token.ExpireTime)
    });
    ```
    

## **常见问题**

在获取Token过程中，可能会出现各种异常问题，整体的排查思路如下：

1.  您可以根据错误码以及错误信息在阿里云官方的[错误代码表](https://help.aliyun.com/zh/api-gateway/error-codes#topic20867)中找到对应错误含义及解决方案。例如，使用一个不存在的AccessKeyId调用服务获取Token时，可能返回如下结果：
    
    ```
    {
      "RequestId":"F5805076-38C5-5093-B5BD-9176ECD9CBBD",
      "Message":"Specified access key is not found.",
      "Recommend":"https://next.api.aliyun.com/troubleshoot?q=InvalidAccessKeyId.NotFound&amp;product=nls-cloud-meta",
      "HostId":"nls-meta.cn-shanghai.aliyuncs.com",
      "Code":"InvalidAccessKeyId.NotFound"
    }
    ```
    
    在[错误代码表](https://help.aliyun.com/zh/api-gateway/error-codes#topic20867)搜索该段代码中错误信息的关键字，例如，**InvalidAccessKeyId.NotFound**和**Specified access key is not found**，可以获取相关错误码详情。![错误码](https://help-static-aliyun-doc.aliyuncs.com/assets/img/zh-CN/8506283661/p494241.png)
    
2.  您也可以在[阿里云OpenAPI诊断中心](https://next.api.aliyun.com/troubleshoot)页面输入错误信息，查找解决方案。
    
    ![api诊断](https://help-static-aliyun-doc.aliyuncs.com/assets/img/zh-CN/8506283661/p494238.png)
    

### **获取Token时出现“Not supported proxy scheme”报错如何处理？**

如果出现“Not supported proxy scheme”报错，建议您检查代理配置是否正确，例如可尝试修改或删除http\_proxy和https\_proxy环境变量并重试。如果您在其他地方设置了代理，请确保代理地址和端口正确，并且代理服务器可用。

## **相关文档**

除了SDK方式获取Token，您还可以通过以下方式获取Token：

-   [通过控制台获取Token](https://help.aliyun.com/zh/isi/getting-started/obtain-an-access-token-in-the-console)
    
-   [通过OpenAPI获取Token](https://help.aliyun.com/zh/isi/getting-started/use-http-or-https-to-obtain-an-access-token)