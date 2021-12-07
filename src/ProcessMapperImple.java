import okhttp3.*;
import org.codehaus.jackson.map.ObjectMapper;

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
        String url = "http://" + getIp() + "/product";

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String body = objectMapper.writeValueAsString(productInfo);

            OkHttpClient okHttpClient = new OkHttpClient();
            RequestBody requestBody = RequestBody.create(
                    MediaType.parse("application/json; " +
                            "charset=UTF-8"), body);

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
                    if (!(responseBody.string().contains("200")) && receiveCount < 3) {
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
