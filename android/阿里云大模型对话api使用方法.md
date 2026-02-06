# 阿里云大模型对话api使用方法

## **选择开发语言**

选择您熟悉的语言或工具，用于调用大模型API。

Python

Node.js

Java

curl

其它语言

### **步骤 1：配置Java环境**

### **检查您的Java版本**

### **安装模型调用SDK**

### **步骤 2：调用大模型API**

您可以运行以下代码来调用大模型API。

```java
import java.util.Arrays;
import java.lang.System;
import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.Constants;

public class Main {
    //  若使用新加坡地域的模型，请释放下列注释
    //  static {Constants.baseHttpApiUrl="https://dashscope-intl.aliyuncs.com/api/v1";}
    public static GenerationResult callWithMessage() throws ApiException, NoApiKeyException, InputRequiredException {
        Generation gen = new Generation();
        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content("You are a helpful assistant.")
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content("你是谁？")
                .build();
        GenerationParam param = GenerationParam.builder()
                // 若没有配置环境变量，请用阿里云百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                // 模型列表：https://help.aliyun.com/model-studio/getting-started/models
                .model("qwen-plus")
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        return gen.call(param);
    }
    public static void main(String[] args) {
        try {
            GenerationResult result = callWithMessage();
            System.out.println(result.getOutput().getChoices().get(0).getMessage().getContent());
        } catch (ApiException | NoApiKeyException | InputRequiredException e) {
            System.err.println("错误信息："+e.getMessage());
            System.out.println("请参考文档：https://help.aliyun.com/model-studio/developer-reference/error-code");
        }
        System.exit(0);
    }
}
```

运行后您将会看到对应的输出结果：

```plaintext
我是阿里云开发的一款超大规模语言模型，我叫通义千问。
```

## **API参考**

- 关于通义千问API的输入输出参数，请参见[通义千问](https://help.aliyun.com/zh/model-studio/qwen-api-reference/)。
- 关于其他模型，请参见[模型列表](https://help.aliyun.com/zh/model-studio/models)。

## **常见问题**

### **[免费额度](https://bailian.console.aliyun.com/#/model-market/detail/qwen-max-latest)**​**用完后如何购买 Token？**

A：您可以访问[费用与成本](https://usercenter2.aliyun.com/home)中心，确保您的账户没有欠费即可调用通义千问模型。

> 调用通义千问模型会自动扣费，出账周期为一小时，消费明细请前往**[账单详情](https://billing-cost.console.aliyun.com/finance/expense-report/expense-detail-by-instance)**进行查看。

### **调用大模型API后报错**​`<b style="box-sizing: border-box; margin: 0px; padding: 0px; font-weight: bolder;">Model.AccessDenied</b>`​ **，如何处理？**

A：该报错是因为您使用子业务空间的API Key，子业务空间无法访问**主账号空间**的应用或模型。使用子空间API Key需由主账号管理员为对应子空间开通模型授权（如本文使用`通义千问-Plus`​模型）。详细操作步骤请参见[设置模型调用权限](https://help.aliyun.com/zh/model-studio/permission-management-overview#f642213a1f38l)。

‍

‍
