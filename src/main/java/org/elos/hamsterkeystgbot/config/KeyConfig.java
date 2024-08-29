package org.elos.hamsterkeystgbot.config;

import java.util.HashMap;
import java.util.Map;

public class KeyConfig {

    // Define a map to hold the configuration data
    public static final Map<String, KeyDetails> KEY_CONFIGS = new HashMap<>();

    // Static block to initialize the configuration data
    static {
        KEY_CONFIGS.put("TRAIN", new KeyDetails("82647f43-3f87-402d-88dd-09a90025313f", "c4480ac7-e178-4973-8061-9ed5b2e17954", 120000));
        KEY_CONFIGS.put("BIKE", new KeyDetails("d28721be-fd2d-4b45-869e-9f253b554e50", "43e35910-c168-4634-ad4f-52fd764a843f", 21000));
        KEY_CONFIGS.put("CUBE", new KeyDetails("d1690a07-3780-4068-810f-9b5bbf2931b2", "b4170868-cef0-424f-8eb9-be0622e8e8e3", 21000));
        KEY_CONFIGS.put("MERGE", new KeyDetails("8d1cc2ad-e097-4b86-90ef-7a27e19fb833", "dc128d28-c45b-411c-98ff-ac7726fbaea4", 20000));
        KEY_CONFIGS.put("TWERK", new KeyDetails("61308365-9d16-4040-8bb0-2f4a4c69074c", "61308365-9d16-4040-8bb0-2f4a4c69074c", 20000));
        KEY_CONFIGS.put("POLYSPHERE", new KeyDetails("2aaf5aee-2cbc-47ec-8a3f-0962cc14bc71", "2aaf5aee-2cbc-47ec-8a3f-0962cc14bc71", 120000));
        KEY_CONFIGS.put("RACE", new KeyDetails("8814a785-97fb-4177-9193-ca4180ff9da8", "8814a785-97fb-4177-9193-ca4180ff9da8", 20000));
        KEY_CONFIGS.put("TRIM", new KeyDetails("ef319a80-949a-492e-8ee0-424fb5fc20a6", "ef319a80-949a-492e-8ee0-424fb5fc20a6", 24000));
        KEY_CONFIGS.put("CAFE", new KeyDetails("bc0971b8-04df-4e72-8a3e-ec4dc663cd11", "bc0971b8-04df-4e72-8a3e-ec4dc663cd11", 22000));
    }

    // Function to get appToken and promoId based on the key
    public static KeyDetails getKeyDetails(String key) {
        return KEY_CONFIGS.get(key);
    }

    // Class to hold the details of each key
    public static class KeyDetails {
        private final String appToken;
        private final String promoId;
        private final int timeout;

        public KeyDetails(String appToken, String promoId, int timeout) {
            this.appToken = appToken;
            this.promoId = promoId;

            this.timeout = timeout;
        }

        public String getAppToken() {
            return appToken;
        }

        public String getPromoId() {
            return promoId;
        }

        @Override
        public String toString() {
            return "KeyDetails{" +
                    "appToken='" + appToken + '\'' +
                    ", promoId='" + promoId + '\'' +
                    '}';
        }

        public int getTimeout() {
            return timeout;
        }
    }

}