# 阿里云 视觉理解api

## **快速开始**

**前提条件**

- 已[获取 API Key](https://help.aliyun.com/zh/model-studio/get-api-key)并[配置API Key到环境变量](https://help.aliyun.com/zh/model-studio/configure-api-key-through-environment-variables)。
- 如果通过 SDK 进行调用，需安装[SDK](https://help.aliyun.com/zh/model-studio/install-sdk)，其中 DashScope Python SDK 版本不低于1.24.6，DashScope Java SDK 版本不低于 2.21.10。

以下示例演示了如何调用模型描述图像内容。关于本地文件和图像限制的说明，请参见[如何传入本地文件](https://help.aliyun.com/zh/model-studio/vision?mode=pure#d987f8de5395x)、[图像限制](https://help.aliyun.com/zh/model-studio/vision?mode=pure#71c2cb6e09ioo)章节。

DashScope

Java

```java
import java.util.Arrays;
import java.util.Collections;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.utils.JsonUtils;
import com.alibaba.dashscope.utils.Constants;

public class Main {

    // 以下为北京地域 base_url，若使用弗吉尼亚地域模型，需要将base_url换成 https://dashscope-us.aliyuncs.com/api/v1
    // 若使用新加坡地域的模型，需将base_url替换为：https://dashscope-intl.aliyuncs.com/api/v1
    static {Constants.baseHttpApiUrl="https://dashscope.aliyuncs.com/api/v1";}
        
    public static void simpleMultiModalConversationCall()
            throws ApiException, NoApiKeyException, UploadFileException {
        MultiModalConversation conv = new MultiModalConversation();
        MultiModalMessage userMessage = MultiModalMessage.builder().role(Role.USER.getValue())
                .content(Arrays.asList(
                        Collections.singletonMap("image", "https://help-static-aliyun-doc.aliyuncs.com/file-manage-files/zh-CN/20241022/emyrja/dog_and_girl.jpeg"),
                        Collections.singletonMap("text", "图中描绘的是什么景象?"))).build();
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                 // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                 // 各地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .model("qwen3-vl-plus")  // 此处以qwen3-vl-plus为例，可按需更换模型名称。模型列表：https://help.aliyun.com/zh/model-studio/models
                .messages(Arrays.asList(userMessage))
                .build();
        MultiModalConversationResult result = conv.call(param);
        System.out.println(result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text"));
    }
    public static void main(String[] args) {
        try {
            simpleMultiModalConversationCall();
        } catch (ApiException | NoApiKeyException | UploadFileException e) {
            System.out.println(e.getMessage());
        }
        System.exit(0);
    }
}
```

## **模型选型**

- 对于如高精度的物体识别与定位（包括 3D 定位）、 Agent 工具调用、文档和网页解析、复杂题目解答、长视频理解等任务，​**首选 Qwen3-VL**，系列内模型对比如下：

  - ​`qwen3-vl-plus`：性能最强的模型。
  - ​`qwen3-vl-flash`：速度更快，成本更低，是兼顾性能与成本的高性价比选择，适用于对响应速度敏感的场景。
- 对于简单的图像描述、短视频摘要提取等通用任务，可选 **Qwen2.5-VL，** 系列内模型对比如下：

  - ​`qwen-vl-max`（属于Qwen2.5-VL）：Qwen2.5-VL 系列中效果最佳的模型。
  - ​`qwen-vl-plus`（属于Qwen2.5-VL）：速度更快，在效果与成本之间实现良好平衡。

这个项目先用`qwen3-vl-flash`模型

## **使用限制**

### **输入文件限制**

图像限制

- **图像分辨率：**

  - 最小尺寸：图像的宽度和高度均须大于`10`像素。
  - 宽高比：图像长边与短边的比值不得超过 `200:1`。
  - 像素上限：

    - 推荐将图像分辨率控制在`8K(7680x4320)`以内。超过此分辨率的图像可能因文件过大、网络传输耗时过长而导致 API 调用超时。
    - 自动缩放机制：模型可通过`max_pixels`​和`min_pixels`调整图像大小；因此，提供超高分辨率的图像并不会提升识别精度，反而会增加调用失败的风险，建议在客户端提前将图像缩放至合理大小。
- **支持的图像格式**

  - 分辨率在4K`(3840x2160)`以下，支持的图像格式如下：

    |**图像格式**|**常见扩展名**|**MIME Type**|
    | ------| -------------------| ------------|
    |BMP|.bmp|image/bmp|
    |JPEG|.jpe, .jpeg, .jpg|image/jpeg|
    |PNG|.png|image/png|
    |TIFF|.tif, .tiff|image/tiff|
    |WEBP|.webp|image/webp|
    |HEIC|.heic|image/heic|
  - 分辨率处于`4K(3840x2160)`​到`8K(7680x4320)`范围，仅支持 JPEG、JPG 、PNG 格式。
- **图像大小：**

  - 以公网 URL 和本地路径传入时：单个图像的大小不超过`10MB`。
  - 以 Base64 编码传入时：编码后的字符串不超过`10MB`。

  > 如需压缩文件体积请参见[如何将图像或视频压缩到满足要求的大小](https://help.aliyun.com/zh/model-studio/vision?mode=pure#ec8e0a8e03moe)。
  >
- **支持传入的图片数量：** 传入多张图像时，图片数量受模型的最大输入的限制，所有图片和文本的总 Token 数必须小于模型的最大输入。

  > 举例说明：使用的模型为`qwen3-vl-plus`​，思考模式下模型的最大输入为`258048`​个`Token`​，若文本消耗`100`​个Token，图像消耗为`2560`​个Token（计算图像的 Token 请参见[计费与限流](https://help.aliyun.com/zh/model-studio/vision?mode=pure#10c14b25cdhuh)），则最多可传入`(258048-100)/ 2560 ≈ 100`张。
  >

### **文件传入方式**

- ​**公网URL**​：提供一个公网可访问的文件地址，支持 HTTP 或 HTTPS 协议。为获得最佳稳定性和性能，可将文件[上传至OSS](https://help.aliyun.com/zh/oss/user-guide/console-quick-start)或[上传文件获取临时URL](https://help.aliyun.com/zh/model-studio/get-temporary-file-url)，获取公网 URL。  
  **重要**

  为确保模型能成功下载文件，提供的公网 URL的请求头中**必须**包含 Content-Length（文件大小）和 Content-Type（媒体类型，如 image/jpeg）。任一字段缺失或者错误将会导致文件下载失败。
- **Base64编码传入：** 将文件转换为 Base64 编码字符串再传入。
- **本地文件路径传入（仅限 DashScope SDK）：** 传入本地文件的路径。

> 关于文件传入方式的建议，请参见[如何选择文件上传方式？](https://help.aliyun.com/zh/model-studio/vision?mode=pure#dc4e7260aauuo)

## **应用于生产环境**

- **图像/视频预处理：** 通义千问VL 对输入的文件有大小限制，如需压缩文件请参见[图像或视频压缩方法](https://help.aliyun.com/zh/model-studio/vision#ec8e0a8e03moe)。
- **处理文本文件：** 通义千问VL 仅支持处理图像格式的文件，无法直接处理文本文件。但可使用以下替代方案：

  - 将文本文件转换为图片格式，建议使用图像处理库（如`Python`​的`pdf2image`​）将文件按页转换为多张高质量的图片，再使用[多图像输入](https://help.aliyun.com/zh/model-studio/vision?mode=pure#f6256b3818huu)方式传入模型。
  - [Qwen-Long](https://help.aliyun.com/zh/model-studio/long-context-qwen-long)支持处理文本文件，可用于解析文件内容。
- **异步与批量处理：** 对于大规模、非实时的图像或视频处理任务，推荐使用[OpenAI兼容-Batch](https://help.aliyun.com/zh/model-studio/batch-interfaces-compatible-with-openai/)的方式（仅支持部分模型）。此方式以异步方式处理任务，并提供50%的成本折扣。
- **容错与稳定性**

  - 超时处理：在非流式调用中，180 秒内模型没有结束输出通常会触发超时报错。为了提升用户体验，超时后响应体中会将已生成的内容返回。如果响应头包含`x-dashscope-partialresponse：true`​，表示本次响应触发了超时。您可以使用[前缀续写](https://help.aliyun.com/zh/model-studio/partial-mode)功能（支持部分模型），将已生成的内容添加到 messages 数组并再次发出请求，使大模型继续生成内容。详情请参见：[基于不完整输出进行续写](https://help.aliyun.com/zh/model-studio/partial-mode#8cc28acfd7a91)。
  - 重试机制：设计合理的API调用重试逻辑（如指数退避），以应对网络波动或服务瞬时不可用的情况。

## **计费与限流**

- **计费 ：** 总费用根据输入和输出的总 Token 数计算；输入和输出价格可参见[模型列表](https://help.aliyun.com/zh/model-studio/models)。

  - **Token 构成：** 输入 Token 由文本 Token 和图像或视频转换后的 Token 组成；输出 Token 为模型生成的文本。在思考模式下，模型的思考过程也会计入输出 Token。若思考模式下未输出思考过程，按照非思考模式价格计费。
  - **计算图像与视频的Token：** 可通过以下代码计算图像或视频的 Token 消耗。估算结果仅供参考，实际用量以 API 响应为准。  
    **计算图像与视频的Token**

    图像

    视频

    计算公式：`图像 Token = h_bar * w_bar / token_pixels + 2`

    - ​`h_bar、w_bar`​：缩放后的图像长宽，模型在处理图像前会进行预处理，会将图像缩小至特定像素上限内，像素上限与`max_pixels`​和`vl_high_resolution_images`​参数的取值有关，相关章节：[处理高分辨率图像](https://help.aliyun.com/zh/model-studio/vision?mode=pure#e7e2db755f9h7)。
    - ​`token_pixels`​：每视觉`Token`对应的像素值，不同模型情况不同：

      - ​`Qwen3-VL`​、`qwen-vl-max`​、`qwen-vl-max-latest`​、`qwen-vl-max-2025-08-13`​、`qwen-vl-plus`​、`qwen-vl-plus-latest`​、`qwen-vl-plus-2025-08-15、qwen-vl-plus-2025-07-10`​ **：** 每个`Token`​对应 `32x32`像素
      - ​`QVQ`​及其他`Qwen2.5-VL`​模型 **：** 每个Token对应`28x28`像素

    以下代码演示了模型内部对图像的大致缩放逻辑，可用于估算一张图像的Token，实际计费请以 API 响应为准。

    ```python
    import math
    # 使用以下命令安装Pillow库：pip install Pillow
    from PIL import Image

    def token_calculate(image_path, max_pixels, vl_high_resolution_images):
        # 打开指定的PNG图片文件
        image = Image.open(image_path)

        # 获取图片的原始尺寸
        height = image.height
        width = image.width

        # 根据不同模型，将宽高调整为32或28的整数倍
        h_bar = round(height / 32) * 32
        w_bar = round(width / 32) * 32

        # 图像的Token下限：4 个 Token
        min_pixels = 4 * 32 * 32
        # 若 vl_high_resolution_images 设置为True，则输入图像Token上限为16386，对应的最大的像素值为16384 * 32 * 32 或 16384 * 28 * 28，否则为max_pixels设置的值
        if vl_high_resolution_images:
            max_pixels = 16384 * 32 * 32
        else:
            max_pixels = max_pixels

        # 对图像进行缩放处理，调整像素的总数在范围[min_pixels,max_pixels]内
        if h_bar * w_bar > max_pixels:
            # 计算缩放因子beta，使得缩放后的图像总像素数不超过max_pixels
            beta = math.sqrt((height * width) / max_pixels)
            # 重新计算调整后的宽高
            h_bar = math.floor(height / beta / 32) * 32
            w_bar = math.floor(width / beta / 32) * 32
        elif h_bar * w_bar < min_pixels:
            # 计算缩放因子beta，使得缩放后的图像总像素数不低于min_pixels
            beta = math.sqrt(min_pixels / (height * width))
            # 重新计算调整后的高度
            h_bar = math.ceil(height * beta / 32) * 32
            w_bar = math.ceil(width * beta / 32) * 32
        return h_bar, w_bar

    if __name__ == "__main__":
        # 将test.png替换为本地的图像路径
        h_bar, w_bar =  token_calculate("xxx/test.jpg", max_pixels=16384*32*32, vl_high_resolution_images=False)
        print(f"缩放后的图像尺寸为：高度为{h_bar}，宽度为{w_bar}")
        # 系统会自动添加<vision_bos>和<vision_eos>视觉标记（各计1个Token）
        token = int((h_bar * w_bar) / (32 * 32))+2
        print(f"图像的Token数为{token}")
    ```
- **查看账单：** 您可以在阿里云控制台的[费用与成本](https://usercenter2.aliyun.com/finance/expense-report/expense-detail)页面查看账单或进行充值。
- **限流：** 通义千问VL模型的限流条件参见[限流](https://help.aliyun.com/zh/model-studio/rate-limit)。
- **免费额度（仅北京地域）** ：从开通百炼或模型申请通过之日起计算有效期，有效期 90 天内，通义千问VL模型提供 100 万 Token 的免费额度。
