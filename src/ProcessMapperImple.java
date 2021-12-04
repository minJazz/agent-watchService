import okhttp3.*;

import java.io.FileReader;
import java.util.Map;
import java.util.Properties;

public class ProcessMapperImple implements ProcessMapper {
    @Override
    public String getIp() {
        String ip = null;

        try {
            Properties properties = new Properties();
            properties.load(new FileReader("src/resources/config.properties"));
            ip = properties.getProperty("ip");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ip;
    }

    @Override
    public void sendProductInfo(Map<String, Integer> productInfo) {
        // 상대 서버 측 IP
        String url = "http://" + getIp() + "/product";
        StringBuffer body = new StringBuffer();
        body.append("{")
                .append("  \"productWeight\":" + productInfo.get("productWeight"))
                .append("}");

        OkHttpClient okHttpClient = new OkHttpClient();
        try {
            //post 요청을 위한 RequestBody 생성
            RequestBody requestBody = RequestBody.create(
                    MediaType.parse("application/json; " +
                            "charset=UTF-8"), body.toString());

            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .post(requestBody);

            Request request = builder.build();

            Response response = okHttpClient.newCall(request).execute();
            ResponseBody responseBody = null;
            int receiveCount = 0;

            if (response.isSuccessful()) {
                responseBody = response.body();
                if (responseBody != null) {
                    if (!(responseBody.string().contains("200")) && receiveCount < 3 ) {
                        response = okHttpClient.newCall(request).execute();
                    }
                    responseBody.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
